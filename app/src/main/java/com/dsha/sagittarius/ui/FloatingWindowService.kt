package com.dsha.sagittarius.ui

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.AppStateEntity
import com.dsha.sagittarius.data.BudgetMath
import com.dsha.sagittarius.data.BudgetRangeType
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.weather.WeatherRepository
import com.dsha.sagittarius.databinding.ItemRecentExpenseBinding
import com.dsha.sagittarius.databinding.ViewFloatingWindowBinding
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.theme.ThemeMode
import com.dsha.sagittarius.util.BudgetPalette
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.GlassPalette
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: BudgetRepository
    private lateinit var weatherRepository: WeatherRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var binding: ViewFloatingWindowBinding? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var currentSnapshot: ActiveBudgetSnapshot? = null
    private var currentState: AppStateEntity? = null
    private var currentCategoryId: Long = 1L
    private var expanded = false
    private var foregroundStarted = false
    private var currentThemeMode: ThemeMode = ThemeManager.current
    private var tokens: ThemeTokens? = null
    private var overlayThemeContext: ContextThemeWrapper? = null

    private val collapsedWindowWidth: Int
        get() = (86f * resources.displayMetrics.density).toInt()
    private val collapsedWindowHeight: Int
        get() = (29f * resources.displayMetrics.density).toInt()
    private val expandedWindowWidth: Int
        get() = (344f * resources.displayMetrics.density).toInt()
            .coerceAtMost(resources.displayMetrics.widthPixels - (16f * resources.displayMetrics.density).toInt())
    private val expandedWindowHeight: Int
        get() {
            val metrics = resources.displayMetrics
            return (metrics.heightPixels * 0.72f).toInt()
                .coerceAtMost(metrics.heightPixels - (24f * metrics.density).toInt())
                .coerceAtLeast((360f * metrics.density).toInt())
        }

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f
    private var dragMoved = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private val reduceMotion by lazy {
        MotionUtils.reduceMotion(this) || MotionUtils.isLowRam(this)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        repository = (application as SagittariusApp).repository
        weatherRepository = (application as SagittariusApp).weatherRepository
        currentThemeMode = ThemeManager.current
        isRunning = true
        createNotificationChannel()

        serviceScope.launch {
            ThemeManager.mode.collect { mode ->
                if (mode != currentThemeMode) {
                    val wasExpanded = expanded
                    currentThemeMode = mode
                    if (binding != null) {
                        removeOverlay()
                        showOverlay()
                        if (wasExpanded) binding?.root?.post { expand() }
                    }
                }
            }
        }
        serviceScope.launch {
            weatherRepository.state.collect { state ->
                binding?.weatherEffect?.setWeather(state.type, state.settings.effectsEnabled)
            }
        }
        serviceScope.launch {
            repository.ensureInitialState()
            launch {
                repository.observeState().collect { state ->
                    currentState = state
                }
            }
            launch {
                repository.observeActiveSnapshot().collect { snapshot ->
                    currentSnapshot = snapshot
                    render(snapshot)
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        ensureForeground()
        return when (action) {
            ACTION_HIDE -> {
                hideOverlay()
                updateNotification()
                START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (binding != null) hideOverlay() else showOverlay()
                updateNotification()
                START_NOT_STICKY
            }
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                serviceScope.launch {
                    runCatching { repository.setFloatingEnabled(false) }
                    stopSelf()
                }
                START_NOT_STICKY
            }
            ACTION_SHOW -> {
                showOverlay()
                START_NOT_STICKY
            }
            else -> {
                // 系统重启或空 Intent 不再自动弹出悬浮窗；必须由用户显式打开。
                closeFloatingWindow()
                START_NOT_STICKY
            }
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
            serviceScope.launch { repository.setFloatingEnabled(false) }
            stopSelf()
            return
        }
        if (binding != null) {
            binding?.root?.visibility = View.VISIBLE
            return
        }

        val themed = ContextThemeWrapper(this, currentThemeMode.themeRes)
        overlayThemeContext = themed
        tokens = ThemeTokens.of(themed)
        val viewBinding = ViewFloatingWindowBinding.inflate(LayoutInflater.from(themed))
        binding = viewBinding
        viewBinding.glass.setCompact(true)
        viewBinding.glass.setPadding(0, 0, 0, 0)
        viewBinding.header.visibility = View.GONE
        weatherRepository.state.value.let { state ->
            viewBinding.weatherEffect.setWeather(state.type, state.settings.effectsEnabled)
        }
        viewBinding.collapsedBody.visibility = View.VISIBLE
        viewBinding.expandedBody.visibility = View.GONE
        val params = createLayoutParams()
        windowParams = params
        setupInteractions(viewBinding)
        try {
            windowManager.addView(viewBinding.root, params)
        } catch (e: Exception) {
            binding = null
            Toast.makeText(
                this,
                "悬浮窗添加失败：${e.message ?: "请检查权限"}",
                Toast.LENGTH_LONG
            ).show()
            serviceScope.launch { repository.setFloatingEnabled(false) }
            stopSelf()
            return
        }
        viewBinding.root.post { clampPosition() }
        serviceScope.launch { repository.setFloatingEnabled(true) }
        currentSnapshot?.let(::render)
        updateNotification()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val metrics = resources.displayMetrics
        val width = collapsedWindowWidth
        val flags = windowFlags(focusable = false)
        return WindowManager.LayoutParams(
            width,
            collapsedWindowHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - width - (8f * metrics.density).toInt()).coerceAtLeast(0)
            y = (96f * metrics.density).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            windowAnimations = 0
        }
    }

    private fun windowFlags(focusable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        // 悬浮窗不使用系统 FLAG_BLUR_BEHIND：部分 HyperOS 会把它扩展成整屏模糊，
        // 导致桌面被糊住且动画卡顿。悬浮窗保留多层静态玻璃质感。
        return flags
    }

    private fun setupInteractions(binding: ViewFloatingWindowBinding) {
        binding.glass.onPaletteChanged = { palette -> applyPalette(palette) }
        applyPalette(BudgetPalette.forRatio(currentSnapshot?.let(BudgetMath::remainingRatio), currentThemeMode == ThemeMode.LIGHT))

        binding.btnCollapse.setOnClickListener { collapse() }
        binding.btnCloseFloating.setOnClickListener { closeFloatingWindow() }
        attachDrag(binding.dragHandle)
        attachDrag(binding.header)
        attachCollapsedTouch(binding.floatingRoot)

        binding.chip7.setOnClickListener {
            selectRange(BudgetRangeType.LAST_7_DAYS)
        }
        binding.chipWeek.setOnClickListener {
            selectRange(BudgetRangeType.THIS_WEEK)
        }
        binding.chipMonth.setOnClickListener {
            selectRange(BudgetRangeType.THIS_MONTH)
        }
        binding.chipLastMonth.setOnClickListener {
            selectRange(BudgetRangeType.LAST_MONTH)
        }
        binding.chipCustom.setOnClickListener {
            showCustomRangePanel()
        }
        binding.btnApplyCustom.setOnClickListener { applyCustomRange() }

        binding.btnBudgetEdit.setOnClickListener {
            binding.budgetEditorPanel.isVisible = !binding.budgetEditorPanel.isVisible
            if (binding.budgetEditorPanel.isVisible) {
                val current = currentSnapshot?.budget?.amountCents
                if (binding.etBudget.text.isNullOrBlank() && current != null) {
                    binding.etBudget.setText(Money.formatPlainCents(current))
                }
                binding.etBudget.requestFocus()
                binding.etBudget.post {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(binding.etBudget, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        binding.btnSaveBudget.setOnClickListener { saveBudgetFromPanel() }

        binding.btnQuickCategory.text = categoryName(currentCategoryId)
        binding.btnQuickCategory.setOnClickListener {
            val categories = currentSnapshot?.categories?.values
                ?.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                .orEmpty()
            if (categories.isEmpty()) return@setOnClickListener
            val index = categories.indexOfFirst { it.id == currentCategoryId }
            currentCategoryId = categories[(index + 1).mod(categories.size)].id
            refreshQuickCategoryLabel()
        }
        binding.btnQuickAdd.setOnClickListener { addQuickExpense() }

        binding.btnAllHistory.setOnClickListener { openHistory() }
        binding.btnFloatingHistory.setOnClickListener { openHistory() }
    }

    private fun attachDrag(handle: View) {
        handle.setOnTouchListener { view, event ->
            val params = windowParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                    dragMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragTouchX
                    val dy = event.rawY - dragTouchY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragMoved = true
                    val metrics = resources.displayMetrics
                    val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
                    val viewHeight = binding?.root?.height?.takeIf { it > 0 } ?: (200f * metrics.density).toInt()
                    val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
                    params.x = (dragStartX + dx.toInt()).coerceIn(0, maxX)
                    params.y = (dragStartY + dy.toInt()).coerceIn(0, maxY)
                    if (binding?.root?.isAttachedToWindow == true) {
                        runCatching { windowManager.updateViewLayout(binding?.root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragMoved) view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun attachCollapsedTouch(root: View) {
        root.isClickable = true
        root.setOnTouchListener { _, event ->
            if (expanded) return@setOnTouchListener false
            val params = windowParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                    dragMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragTouchX
                    val dy = event.rawY - dragTouchY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragMoved = true
                    val metrics = resources.displayMetrics
                    val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
                    val viewHeight = binding?.root?.height?.takeIf { it > 0 }
                        ?: (30f * metrics.density).toInt()
                    val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
                    params.x = (dragStartX + dx.toInt()).coerceIn(0, maxX)
                    params.y = (dragStartY + dy.toInt()).coerceIn(0, maxY)
                    if (binding?.root?.isAttachedToWindow == true) {
                        runCatching { windowManager.updateViewLayout(binding?.root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragMoved && event.actionMasked == MotionEvent.ACTION_UP) {
                        root.performClick()
                        expand()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setWindowSize(expandedNow: Boolean) {
        val binding = binding ?: return
        val params = windowParams ?: return
        val metrics = resources.displayMetrics
        val newWidth = if (expandedNow) expandedWindowWidth else collapsedWindowWidth
        val newHeight = if (expandedNow) expandedWindowHeight else collapsedWindowHeight
        val oldRight = params.x + params.width
        params.width = newWidth
        params.height = newHeight
        params.x = (oldRight - newWidth).coerceIn(
            0,
            (metrics.widthPixels - newWidth).coerceAtLeast(0)
        )
        params.flags = windowFlags(focusable = expandedNow)
        params.softInputMode = if (expandedNow) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        if (binding.root.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(binding.root, params) }
        }
        binding.root.post { clampPosition() }
    }

    private fun expand() {
        val binding = binding ?: return
        if (expanded) return
        expanded = true
        binding.header.visibility = View.VISIBLE
        binding.collapsedBody.visibility = View.GONE
        binding.expandedBody.visibility = View.VISIBLE
        binding.expandedBody.alpha = 0f
        binding.expandedBody.scaleY = 0.9f
        binding.expandedBody.pivotY = 0f
        binding.btnCollapse.isVisible = true
        binding.glass.setExpanded(true)
        binding.glass.setCompact(false)
        val density = resources.displayMetrics.density
        binding.glass.setPadding(
            (16f * density).toInt(),
            (13f * density).toInt(),
            (16f * density).toInt(),
            (15f * density).toInt()
        )
        setWindowSize(true)

        if (reduceMotion) {
            binding.glass.scaleX = 1f
            binding.glass.scaleY = 1f
            binding.expandedBody.scaleY = 1f
            binding.expandedBody.alpha = 1f
        } else {
            binding.glass.scaleX = 1.05f
            binding.glass.scaleY = 0.90f
            SpringAnimation(binding.glass, DynamicAnimation.SCALE_X, 1f).apply {
                spring.dampingRatio = 0.42f
                spring.stiffness = 300f
                start()
            }
            SpringAnimation(binding.glass, DynamicAnimation.SCALE_Y, 1f).apply {
                spring.dampingRatio = 0.42f
                spring.stiffness = 300f
                start()
            }
            SpringAnimation(binding.expandedBody, DynamicAnimation.SCALE_Y, 1f).apply {
                spring.dampingRatio = 0.6f
                spring.stiffness = 260f
                start()
            }
            ObjectAnimator.ofFloat(binding.expandedBody, View.ALPHA, 0f, 1f).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                start()
            }
        }
        binding.root.post { clampPosition() }
    }

    private fun collapse() {
        val binding = binding ?: return
        if (!expanded) return
        expanded = false
        binding.btnCollapse.isVisible = false
        hideKeyboard()
        binding.header.visibility = View.GONE
        binding.expandedBody.visibility = View.GONE
        binding.expandedBody.alpha = 1f
        binding.expandedBody.scaleY = 1f
        binding.collapsedBody.visibility = View.VISIBLE
        binding.customRangePanel.visibility = View.GONE
        binding.budgetEditorPanel.visibility = View.GONE
        binding.glass.setExpanded(false)
        binding.glass.setCompact(true)
        binding.glass.setPadding(0, 0, 0, 0)
        setWindowSize(false)
        if (reduceMotion) {
            binding.glass.scaleX = 1f
            binding.glass.scaleY = 1f
        } else {
            binding.glass.scaleX = 0.82f
            binding.glass.scaleY = 0.7f
            SpringAnimation(binding.glass, DynamicAnimation.SCALE_X, 1f).apply {
                spring.dampingRatio = 0.55f
                spring.stiffness = 320f
                start()
            }
            SpringAnimation(binding.glass, DynamicAnimation.SCALE_Y, 1f).apply {
                spring.dampingRatio = 0.55f
                spring.stiffness = 320f
                start()
            }
        }
    }

    private fun hideOverlay() {
        removeOverlay()
        serviceScope.launch { repository.setFloatingEnabled(false) }
        updateNotification()
    }

    private fun closeFloatingWindow() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.launch {
            runCatching { repository.setFloatingEnabled(false) }
            stopSelf()
        }
    }

    private fun removeOverlay() {
        val view = binding?.root
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        binding?.glass?.onPaletteChanged = null
        binding = null
        windowParams = null
        tokens = null
        overlayThemeContext = null
        expanded = false
        hideKeyboard()
    }

    private fun render(snapshot: ActiveBudgetSnapshot) {
        val binding = binding ?: return
        val state = snapshot.state ?: return
        binding.tvRangeFloating.text = DateText.rangeLabel(
            state.activeRangeType,
            state.activeStartDate,
            state.activeEndDate
        )
        val ratio = BudgetMath.remainingRatio(snapshot)
        binding.glass.setRemainingRatio(ratio)
        binding.progressFloating.setProgress(snapshot.usedRatio ?: 0f)
        selectChip(state.activeRangeType)

        if (!snapshot.isConfigured) {
            binding.tvCollapsedValue.text = "未设置"
            binding.tvRemainingFloating.text = getString(R.string.not_set_budget)
            binding.tvRemainingFloating.textSize = 26f
            binding.tvPercentFloating.text = "先设置预算"
            binding.tvBudgetFloating.text = "展开后可设置日期范围与预算金额"
            binding.progressFloating.setProgress(0f)
            renderRecent(snapshot.recent, snapshot.categories)
        } else {
            binding.tvCollapsedValue.text =
                Money.formatSignedCents(snapshot.remainingCents ?: 0L)
            binding.tvRemainingFloating.text = Money.formatSignedCents(snapshot.remainingCents ?: 0L)
            binding.tvRemainingFloating.textSize = 30f
            val remainingPercent = Money.remainingPercent(ratio) ?: 0
            binding.tvPercentFloating.text = if (remainingPercent >= 0) {
                "剩余 $remainingPercent%"
            } else {
                "已超支 ${abs(remainingPercent)}%"
            }
            binding.tvBudgetFloating.text =
                "预算 ${Money.formatCents(snapshot.budget?.amountCents ?: 0L)} · " +
                "已用 ${Money.formatCents(snapshot.totalCents)}"
            renderRecent(snapshot.recent, snapshot.categories)
        }
        currentSnapshot = snapshot
        val categories = snapshot.categories.values
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        if (categories.isNotEmpty() && categories.none { it.id == currentCategoryId }) {
            currentCategoryId = categories.first().id
        }
        refreshQuickCategoryLabel()
    }

    private fun renderRecent(
        expenses: List<com.dsha.sagittarius.data.ExpenseEntity>,
        categories: Map<Long, CategoryEntity>
    ) {
        val binding = binding ?: return
        binding.recentContainerFloating.removeAllViews()
        binding.tvNoRecentFloating.isVisible = expenses.isEmpty()
        expenses.forEach { expense ->
            val row = ItemRecentExpenseBinding.inflate(
                LayoutInflater.from(overlayThemeContext ?: this),
                binding.recentContainerFloating,
                false
            )
            bindRecentRow(row.root, expense, categories[expense.categoryId])
            row.root.setOnClickListener { openHistory() }
            binding.recentContainerFloating.addView(row.root)
        }
    }

    private fun refreshQuickCategoryLabel() {
        val binding = binding ?: return
        val name = categoryName(currentCategoryId)
        binding.btnQuickCategory.text = name
        binding.btnQuickCategory.announceForAccessibility(name)
    }

    private fun categoryName(id: Long): String {
        val category = currentSnapshot?.categories?.get(id)
        return category?.let { CategoryCatalog.displayName(this, it) }
            ?: getString(R.string.category_unknown)
    }

    private fun applyPalette(palette: GlassPalette) {
        val binding = binding ?: return
        binding.tvRemainingFloating.setTextColor(palette.display)
        binding.tvCollapsedValue.setTextColor(palette.display)
        binding.progressFloating.setPalette(palette.primary, palette.secondary, animate = false)
        binding.stateDot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(palette.display)
    }

    private fun selectChip(type: BudgetRangeType) {
        val binding = binding ?: return
        val chips = mapOf(
            BudgetRangeType.LAST_7_DAYS to binding.chip7,
            BudgetRangeType.THIS_WEEK to binding.chipWeek,
            BudgetRangeType.THIS_MONTH to binding.chipMonth,
            BudgetRangeType.LAST_MONTH to binding.chipLastMonth,
            BudgetRangeType.CUSTOM to binding.chipCustom
        )
        chips.forEach { (rangeType, chip) ->
            val selected = rangeType == type
            chip.isSelected = selected
            chip.setTextColor(
                if (selected) tokens?.chipSelectedText ?: Color.WHITE
                else tokens?.chipUnselectedText ?: Color.LTGRAY
            )
        }
    }

    private fun selectRange(type: BudgetRangeType) {
        val binding = binding ?: return
        binding.customRangePanel.visibility = View.GONE
        serviceScope.launch { repository.setActiveRange(type) }
    }

    private fun showCustomRangePanel() {
        val binding = binding ?: return
        val state = currentState
        binding.customRangePanel.isVisible = true
        if (binding.etCustomStart.text.isNullOrBlank()) {
            binding.etCustomStart.setText(
                DateText.iso(state?.activeStartDate ?: LocalDate.now().withDayOfMonth(1))
            )
        }
        if (binding.etCustomEnd.text.isNullOrBlank()) {
            binding.etCustomEnd.setText(
                DateText.iso(state?.activeEndDate ?: LocalDate.now())
            )
        }
    }

    private fun applyCustomRange() {
        val binding = binding ?: return
        val start = runCatching {
            LocalDate.parse(binding.etCustomStart.text?.toString()?.trim())
        }.getOrNull()
        val end = runCatching {
            LocalDate.parse(binding.etCustomEnd.text?.toString()?.trim())
        }.getOrNull()
        if (start == null) {
            binding.etCustomStart.error = "格式：yyyy-MM-dd"
            return
        }
        if (end == null) {
            binding.etCustomEnd.error = "格式：yyyy-MM-dd"
            return
        }
        if (start.isAfter(end)) {
            Toast.makeText(this, R.string.custom_range_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        binding.customRangePanel.visibility = View.GONE
        serviceScope.launch {
            repository.setActiveRange(BudgetRangeType.CUSTOM, customStart = start, customEnd = end)
        }
    }

    private fun saveBudgetFromPanel() {
        val binding = binding ?: return
        val cents = Money.parseYuanToCents(binding.etBudget.text?.toString().orEmpty())
        if (cents == null || cents <= 0L) {
            binding.etBudget.error = getString(R.string.budget_invalid)
            return
        }
        serviceScope.launch {
            val accepted = repository.setBudgetForActiveRange(cents)
            if (!accepted) {
                Toast.makeText(
                    this@FloatingWindowService,
                    "新预算低于预算树已分配金额，请先调整预算树",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            binding.budgetEditorPanel.visibility = View.GONE
            binding.etBudget.clearFocus()
            hideKeyboard()
            Toast.makeText(
                this@FloatingWindowService,
                R.string.floating_budget_saved,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun addQuickExpense() {
        val binding = binding ?: return
        val cents = Money.parseYuanToCents(binding.etQuickAmount.text?.toString().orEmpty())
        if (cents == null || cents <= 0L) {
            binding.etQuickAmount.error = getString(R.string.invalid_amount)
            return
        }
        val note = binding.etQuickNote.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        serviceScope.launch {
            repository.addExpense(cents, LocalDate.now(), currentCategoryId, note)
            binding.etQuickAmount.text?.clear()
            binding.etQuickNote.text?.clear()
            binding.etQuickAmount.clearFocus()
            binding.etQuickNote.clearFocus()
            hideKeyboard()
            Toast.makeText(
                this@FloatingWindowService,
                R.string.floating_quick_saved,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openHistory() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_HISTORY)
        }
        startActivity(intent)
    }

    private fun hideKeyboard() {
        val view = binding?.root ?: return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun clampPosition() {
        val binding = binding ?: return
        val params = windowParams ?: return
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
        val height = binding.root.height.takeIf { it > 0 } ?: (200f * metrics.density).toInt()
        val maxY = (metrics.heightPixels - height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
        if (binding.root.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(binding.root, params) }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.floating_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.floating_channel_desc)
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val snapshot = currentSnapshot
        val state = snapshot?.state ?: currentState
        val visible = binding != null
        val text = if (!visible) {
            getString(R.string.notification_text_hidden)
        } else if (snapshot?.isConfigured == true) {
            getString(
                R.string.notification_text,
                state?.let {
                    DateText.rangeLabelCompact(
                        it.activeRangeType,
                        it.activeStartDate,
                        it.activeEndDate
                    )
                } ?: "",
                Money.formatSignedCents(snapshot.remainingCents ?: 0L)
            )
        } else {
            getString(R.string.not_set_budget)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            REQ_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePending = PendingIntent.getService(
            this,
            REQ_TOGGLE,
            Intent(this, FloatingWindowService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val exitPending = PendingIntent.getService(
            this,
            REQ_EXIT,
            Intent(this, FloatingWindowService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(tokens?.accent ?: ContextCompat.getColor(this, R.color.accent_teal))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(if (visible) R.string.notification_hide else R.string.notification_show),
                togglePending
            )
            .addAction(0, getString(R.string.notification_open), openPending)
            .addAction(0, getString(R.string.notification_exit), exitPending)
            .build()
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // 通知权限被关闭时，前台服务仍然继续；不要因为刷新通知而崩溃。
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = windowParams ?: return
        params.width = if (expanded) expandedWindowWidth else collapsedWindowWidth
        params.height = if (expanded) expandedWindowHeight else collapsedWindowHeight
        binding?.root?.post { clampPosition() }
    }

    override fun onDestroy() {
        isRunning = false
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_SHOW = "com.dsha.sagittarius.action.SHOW_FLOATING"
        const val ACTION_HIDE = "com.dsha.sagittarius.action.HIDE_FLOATING"
        const val ACTION_TOGGLE = "com.dsha.sagittarius.action.TOGGLE_FLOATING"
        const val ACTION_STOP = "com.dsha.sagittarius.action.STOP_FLOATING"

        private const val CHANNEL_ID = "budget_floating_channel"
        private const val NOTIFICATION_ID = 4101
        private const val REQ_OPEN = 20
        private const val REQ_TOGGLE = 21
        private const val REQ_EXIT = 22
    }
}
