package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.weather.WeatherCity
import com.dsha.sagittarius.data.weather.WeatherRepository
import com.dsha.sagittarius.data.weather.WeatherType
import com.dsha.sagittarius.databinding.ViewDateConsoleBinding
import com.dsha.sagittarius.theme.ThemeManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 统一顶栏日期控制台。
 *
 * 手势只在本 View 内改变日期/量程；水平/垂直拖动时禁止 ViewPager2 拦截。
 */
class DateConsoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ViewDateConsoleBinding.inflate(LayoutInflater.from(context), this)
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tokens = ThemeTokens.of(context)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val rect = RectF()
    private val effectRenderer = WeatherEffectRenderer(
        resources.displayMetrics.density,
        ThemeManager.isLight,
        !MotionUtils.isLowRam(context)
    )
    private val effectClipPath = Path()
    private val effectRect = RectF()
    private var weatherType: WeatherType? = null
    private var weatherEffectsEnabled = false
    private var effectPhase = 0f
    private var weatherJob: Job? = null
    private var lifecycleActive = false

    private val effectAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12_000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            effectPhase = it.animatedValue as Float
            invalidate()
        }
    }

    private var weatherMode = false
    private var weatherCities: List<WeatherCity> = emptyList()
    private var weatherCityIndex = 0
    private var weatherDisplayName: String? = null
    private var onWeatherCitySelected: ((WeatherCity) -> Unit)? = null

    private var page: DateConsolePage? = null
    private var state: DateConsoleState? = null
    private var style: DateConsoleStyle = DateConsoleStyleManager.style.value
    private var consoleImage: Bitmap? = null
    private var consoleImageShader: BitmapShader? = null
    private var consoleImageShaderBitmap: Bitmap? = null
    private var imageJob: Job? = null
    private val imageMatrix = Matrix()
    private val imageScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var downX = 0f
    private var downY = 0f
    private var swipeMode = 0
    private var steps = 0

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        setWillNotDraw(false)
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        binding.btnConsoleLock.setOnClickListener {
            Haptics.tap(binding.btnConsoleLock)
            page?.let { DateConsoleManager.toggleLocked(it) }
        }
        binding.btnConsoleRange.setOnClickListener {
            val current = state ?: return@setOnClickListener
            val range = DateConsoleManager.rangeOf(current)
            CustomRangePicker.show(context, range.start, range.end) { start, end ->
                page?.let { DateConsoleManager.setCustomRange(it, start, end) }
            }
        }
        binding.btnConsoleColor.setOnClickListener { showColorPanel() }
        setupButtonIcons()
        applyStyle(style)
    }

    private fun observeConsoleImage() {
        val owner = findViewTreeLifecycleOwner() ?: return
        imageJob?.cancel()
        imageJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DateConsoleImageManager.path.collect { path ->
                    consoleImage = if (path.isNullOrBlank()) {
                        null
                    } else {
                        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                    }
                    consoleImageShader = null
                    consoleImageShaderBitmap = null
                    invalidate()
                }
            }
        }
    }

    fun bind(page: DateConsolePage, @androidx.annotation.StringRes titleRes: Int = 0) {
        this.page = page
        weatherMode = false
        binding.tvConsoleTitle.text = context.getString(if (titleRes != 0) titleRes else page.titleRes)
        binding.btnConsoleLock.visibility = View.VISIBLE
        binding.btnConsoleRange.visibility = View.VISIBLE
        binding.btnConsoleColor.visibility = View.VISIBLE
        state = DateConsoleManager.stateOf(page).value
        renderState(state!!)
        contentDescription = context.getString(R.string.date_console_swipe_hint)
    }

    fun bindWeather(
        cities: List<WeatherCity>,
        selectedCityId: String,
        onSelected: (WeatherCity) -> Unit
    ) {
        page = null
        weatherMode = true
        weatherCities = cities
        weatherCityIndex = cities.indexOfFirst { it.id == selectedCityId }.coerceAtLeast(0)
        weatherDisplayName = null
        onWeatherCitySelected = onSelected
        binding.tvConsoleTitle.text = context.getString(R.string.tab_weather)
        binding.btnConsoleLock.visibility = View.GONE
        binding.btnConsoleRange.visibility = View.GONE
        binding.btnConsoleColor.visibility = View.VISIBLE
        binding.btnConsoleLock.isSelected = false
        renderWeatherCity(0)
        contentDescription = context.getString(R.string.weather_city_swipe_hint)
    }

    fun setWeatherCity(selectedCityId: String, displayName: String? = null) {
        if (!weatherMode || weatherCities.isEmpty()) return
        val index = weatherCities.indexOfFirst { it.id == selectedCityId }
        if (index >= 0) weatherCityIndex = index
        weatherDisplayName = displayName
        renderWeatherCity(0)
    }

    private fun renderWeatherCity(previewSteps: Int) {
        if (weatherCities.isEmpty()) return
        val current = wrapCity(weatherCityIndex + previewSteps)
        binding.tvConsoleDate.text = if (previewSteps == 0 && !weatherDisplayName.isNullOrBlank()) {
            weatherDisplayName
        } else {
            weatherCities[current].name
        }
        binding.tvConsoleDatePrev.text = weatherCities[wrapCity(current - 1)].name
        binding.tvConsoleDateNext.text = weatherCities[wrapCity(current + 1)].name
        binding.tvConsoleDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28f)
    }

    private fun wrapCity(index: Int): Int {
        if (weatherCities.isEmpty()) return 0
        var value = index % weatherCities.size
        if (value < 0) value += weatherCities.size
        return value
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeConsoleImage()
        val owner = findViewTreeLifecycleOwner() ?: return
        weatherJob?.cancel()
        weatherJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                lifecycleActive = true
                try {
                    WeatherRepository.getInstance(context).state.collect { state ->
                        weatherType = state.type
                        weatherEffectsEnabled = state.settings.effectsEnabled
                        updateWeatherAnimation()
                    }
                } finally {
                    lifecycleActive = false
                    updateWeatherAnimation()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        imageJob?.cancel()
        imageJob = null
        weatherJob?.cancel()
        weatherJob = null
        effectAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateWeatherAnimation()
    }

    private fun updateWeatherAnimation() {
        val shouldAnimate = weatherEffectsEnabled && weatherType != null && lifecycleActive &&
            isAttachedToWindow && isShown && MotionUtils.ambientAllowed(context)
        if (shouldAnimate && !effectAnimator.isRunning) {
            effectAnimator.start()
        } else if (!shouldAnimate && effectAnimator.isRunning) {
            effectAnimator.cancel()
            effectPhase = 0f
        }
        invalidate()
    }

    fun setState(newState: DateConsoleState) {
        state = newState
        if (swipeMode == 0) renderState(newState)
    }

    fun setStyle(newStyle: DateConsoleStyle) {
        style = newStyle
        applyStyle(newStyle)
    }

    private fun renderState(newState: DateConsoleState) {
        binding.tvConsoleDate.text = DateConsoleManager.label(newState)
        binding.tvConsoleDatePrev.text = DateConsoleManager.shiftedLabel(newState, -1)
        binding.tvConsoleDateNext.text = DateConsoleManager.shiftedLabel(newState, 1)
        binding.tvConsoleDate.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            if (newState.isCustom) 23f else 30f
        )
        binding.btnConsoleLock.isSelected = newState.locked
        binding.dateRow.translationX = 0f
        binding.tvConsoleDate.alpha = 1f
        applyButtonStyles()
    }

    private fun applyStyle(newStyle: DateConsoleStyle) {
        val darkText = if (newStyle.isTheme) {
            false
        } else {
            !DateConsoleStyleManager.isLight(newStyle)
        }
        val primary = when {
            newStyle.isTheme -> tokens.textPrimary
            darkText -> Color.parseColor("#F8FAFC")
            else -> Color.parseColor("#172033")
        }
        val secondary = when {
            newStyle.isTheme -> tokens.textSecondary
            darkText -> Color.parseColor("#B7C2D4")
            else -> Color.parseColor("#5A6477")
        }
        binding.tvConsoleDate.setTextColor(primary)
        binding.tvConsoleDatePrev.setTextColor(ColorUtils.setAlphaComponent(secondary, 150))
        binding.tvConsoleDateNext.setTextColor(ColorUtils.setAlphaComponent(secondary, 150))
        binding.tvConsoleTitle.setTextColor(ColorUtils.setAlphaComponent(secondary, 210))
        applyButtonStyles()
        invalidate()
    }

    private fun applyButtonStyles() {
        val iconColor = when {
            style.isTheme -> tokens.textPrimary
            else -> DateConsoleStyleManager.iconTint(style)
        }
        val buttonFill = ColorUtils.setAlphaComponent(iconColor, 26)
        val buttonStroke = ColorUtils.setAlphaComponent(iconColor, 55)
        listOf(binding.btnConsoleLock, binding.btnConsoleRange, binding.btnConsoleColor)
            .forEach { button ->
                val tint = if (button == binding.btnConsoleLock && state?.locked == true) {
                    Color.WHITE
                } else {
                    iconColor
                }
                TextViewCompat.setCompoundDrawableTintList(button, ColorStateList.valueOf(tint))
                if (button == binding.btnConsoleLock && state?.locked == true) {
                    button.background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(tokens.gradientStart, tokens.gradientMid)
                    ).apply {
                        cornerRadius = dpF(12f)
                        setStroke(dp(1f), ColorUtils.setAlphaComponent(Color.WHITE, 80))
                    }
                } else {
                    button.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpF(12f)
                        setColor(buttonFill)
                        setStroke(dp(1f), buttonStroke)
                    }
                }
            }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (weatherMode) return handleWeatherTouch(event)
        val current = state ?: return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swipeMode = 0
                steps = 0
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (current.locked) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (swipeMode == 0) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        swipeMode = 1
                    } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        swipeMode = 2
                    }
                }
                if (swipeMode == 1) {
                    // 左滑 dx<0：未来；右滑 dx>0：过去。与常规日期分页方向一致。
                    val nextSteps = (-dx / (width.coerceAtLeast(1) * 0.5f)).roundToInt()
                    if (nextSteps != steps) {
                        steps = nextSteps
                        binding.tvConsoleDate.text = DateConsoleManager.shiftedLabel(current, steps)
                        binding.tvConsoleDatePrev.text =
                            DateConsoleManager.shiftedLabel(current, steps - 1)
                        binding.tvConsoleDateNext.text =
                            DateConsoleManager.shiftedLabel(current, steps + 1)
                    }
                    binding.dateRow.translationX =
                        (dx * 0.16f).coerceIn(-dpF(18f), dpF(18f))
                    binding.tvConsoleDate.alpha =
                        (1f - (abs(dx) / width.coerceAtLeast(1) * 0.5f)).coerceIn(0.55f, 1f)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val oldMode = swipeMode
                if (!current.locked) {
                    if (swipeMode == 1 && steps != 0) {
                        page?.let { DateConsoleManager.shift(it, steps) }
                        Haptics.edge(binding.dateRow)
                    } else if (swipeMode == 2) {
                        val dy = event.y - downY
                        if (abs(dy) > touchSlop) {
                            page?.let {
                                DateConsoleManager.changeGranularity(it, if (dy < 0) 1 else -1)
                            }
                        }
                    }
                }
                SpringAnimation(binding.dateRow, DynamicAnimation.TRANSLATION_X, 0f).apply {
                    spring.dampingRatio = 0.72f
                    spring.stiffness = 520f
                    start()
                }
                binding.tvConsoleDate.animate().alpha(1f).setDuration(180L).start()
                if (oldMode != 0) {
                    // 提交或回弹后都从全局状态重新取日期，避免预览值残留。
                    val latest = page?.let { DateConsoleManager.stateOf(it).value } ?: state
                    if (latest != null) {
                        state = latest
                        renderState(latest)
                    }
                }
                swipeMode = 0
                steps = 0
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleWeatherTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swipeMode = 0
                steps = 0
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (swipeMode == 0) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        swipeMode = 1
                    } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        swipeMode = 2
                    }
                }
                if (swipeMode == 1) {
                    val nextSteps = (-dx / (width.coerceAtLeast(1) * 0.5f)).roundToInt()
                    if (nextSteps != steps) {
                        steps = nextSteps
                        renderWeatherCity(steps)
                    }
                    binding.dateRow.translationX = (dx * 0.16f).coerceIn(-dpF(18f), dpF(18f))
                    binding.tvConsoleDate.alpha =
                        (1f - (abs(dx) / width.coerceAtLeast(1) * 0.5f)).coerceIn(0.55f, 1f)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                SpringAnimation(binding.dateRow, DynamicAnimation.TRANSLATION_X, 0f).apply {
                    spring.dampingRatio = 0.72f
                    spring.stiffness = 520f
                    start()
                }
                binding.tvConsoleDate.animate().alpha(1f).setDuration(180L).start()
                if (swipeMode == 1 && steps != 0) {
                    weatherCityIndex = wrapCity(weatherCityIndex + steps)
                    weatherCities.getOrNull(weatherCityIndex)?.let { city ->
                        onWeatherCitySelected?.invoke(city)
                    }
                }
                weatherCityIndex = weatherCityIndex.coerceIn(0, (weatherCities.size - 1).coerceAtLeast(0))
                weatherDisplayName = null
                renderWeatherCity(0)
                swipeMode = 0
                steps = 0
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val start: Int
        val end: Int
        if (style.isTheme) {
            start = tokens.cardFillTop
            end = tokens.cardFillBottom
        } else {
            start = style.startColor ?: tokens.cardFillTop
            end = style.endColor ?: tokens.cardFillBottom
        }
        rect.set(1f, 1f, width - 1f, height - 1f)
        val bitmap = consoleImage
        if (bitmap != null) {
            if (consoleImageShader == null || consoleImageShaderBitmap !== bitmap) {
                consoleImageShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                consoleImageShaderBitmap = bitmap
            }
            val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dx = (width - bitmap.width * scale) / 2f
            val dy = (height - bitmap.height * scale) / 2f
            imageMatrix.setScale(scale, scale)
            imageMatrix.postTranslate(dx, dy)
            consoleImageShader?.setLocalMatrix(imageMatrix)
            backgroundPaint.shader = consoleImageShader
            canvas.drawRoundRect(rect, dpF(24f), dpF(24f), backgroundPaint)
            backgroundPaint.shader = null
            // 磨砂蒙层：保证日期、小字和图标在任意图片上可读。
            val darkText = if (style.isTheme) !ThemeManager.isLight else !DateConsoleStyleManager.isLight(style)
            imageScrimPaint.color = if (darkText) {
                Color.argb(118, 255, 255, 255)
            } else {
                Color.argb(88, 4, 10, 20)
            }
            canvas.drawRoundRect(rect, dpF(24f), dpF(24f), imageScrimPaint)
        } else {
            backgroundPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                start, end, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, dpF(24f), dpF(24f), backgroundPaint)
            backgroundPaint.shader = null
        }
        borderPaint.color = if (style.isTheme) {
            tokens.cardStrokeStart
        } else {
            ColorUtils.setAlphaComponent(Color.WHITE, 55)
        }
        canvas.drawRoundRect(rect, dpF(24f), dpF(24f), borderPaint)
        // 顶部高光，保持玻璃卡片质感。
        borderPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 36)
        canvas.drawLine(
            dpF(24f), dpF(2f), width - dpF(24f), dpF(2f), borderPaint
        )
        val effect = weatherType
        if (weatherEffectsEnabled && effect != null) {
            effectRect.set(1f, 1f, width - 1f, height - 1f)
            effectClipPath.reset()
            effectClipPath.addRoundRect(effectRect, dpF(24f), dpF(24f), Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(effectClipPath)
            effectRenderer.draw(
                canvas,
                effectRect,
                dpF(24f),
                effect,
                effectPhase * 12f,
                compact = false,
                reducedMotion = !MotionUtils.ambientAllowed(context),
                lowQuality = MotionUtils.isLowRam(context)
            )
            canvas.restoreToCount(save)
        }
        super.onDraw(canvas)
    }

    private fun setupButtonIcons() {
        setIcon(binding.btnConsoleLock, R.drawable.ic_console_lock)
        setIcon(binding.btnConsoleRange, R.drawable.ic_console_calendar)
        setIcon(binding.btnConsoleColor, R.drawable.ic_console_palette)
    }

    private fun setIcon(button: TextView, res: Int) {
        val drawable = AppCompatResources.getDrawable(context, res) ?: return
        val size = dp(18f)
        drawable.setBounds(0, 0, size, size)
        button.text = ""
        button.setPadding(dp(10f), 0, dp(10f), 0)
        button.setCompoundDrawables(drawable, null, null, null)
    }

    private fun showColorPanel() {
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10f), dp(6f), dp(10f), dp(2f))
        }
        val colorTab = TextView(context).apply {
            text = context.getString(R.string.date_console_color_title)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(8f))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val imageTab = TextView(context).apply {
            text = "图片背景"
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(8f))
            textSize = 13f
        }
        tabs.addView(colorTab, LinearLayout.LayoutParams(0, dp(36f), 1f))
        tabs.addView(imageTab, LinearLayout.LayoutParams(0, dp(36f), 1f))
        root.addView(tabs)
        val content = FrameLayout(context)
        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val colorGrid = GridLayout(context).apply {
            columnCount = 3
            setPadding(dp(12f), dp(6f), dp(12f), dp(10f))
        }
        DateConsoleStyleManager.presets.forEachIndexed { index, item ->
            val swatch = TextView(context).apply {
                text = item.label
                gravity = android.view.Gravity.CENTER
                textSize = 11f
                setTextColor(
                    if (item.isTheme) tokens.textPrimary
                    else if (DateConsoleStyleManager.isLight(item)) Color.parseColor("#172033") else Color.WHITE
                )
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        item.startColor ?: tokens.cardFillTop,
                        item.endColor ?: tokens.cardFillBottom
                    )
                ).apply {
                    cornerRadius = dpF(12f)
                    setStroke(dp(1f), ColorUtils.setAlphaComponent(Color.WHITE, 60))
                }
                setOnClickListener {
                    setStyle(item)
                    DateConsoleStyleManager.setStyle(context, item)
                    dialog.dismiss()
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3),
                GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = dp(52f)
                setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
            }
            colorGrid.addView(swatch, params)
        }

        val imagePanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(20f), dp(12f), dp(20f), dp(18f))
        }
        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(tokens.iconCircle)
        }
        consoleImage?.let { preview.setImageBitmap(it) }
        imagePanel.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(110f)
        ))
        val upload = TextView(context).apply {
            text = if (consoleImage == null) "上传图片" else "更换图片"
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tokens.gradientStart, tokens.gradientMid)
            ).apply { cornerRadius = dpF(14f) }
            setOnClickListener {
                context.startActivity(
                    ImageCropActivity.intent(context, 3, 1, true, "console")
                )
                dialog.dismiss()
            }
        }
        imagePanel.addView(upload, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(46f)
        ).apply { topMargin = dp(10f) })
        if (consoleImage != null) {
            val remove = TextView(context).apply {
                text = "恢复渐变背景"
                gravity = android.view.Gravity.CENTER
                setTextColor(tokens.textSecondary)
                textSize = 13f
                setPadding(0, dp(8f), 0, 0)
                setOnClickListener {
                    DateConsoleImageManager.setPath(context, null)
                    dialog.dismiss()
                }
            }
            imagePanel.addView(remove)
        }
        content.addView(colorGrid)
        content.addView(imagePanel)
        imagePanel.visibility = View.GONE

        fun selectImage(selected: Boolean) {
            colorGrid.visibility = if (selected) View.GONE else View.VISIBLE
            imagePanel.visibility = if (selected) View.VISIBLE else View.GONE
            colorTab.setTextColor(if (selected) tokens.textTertiary else tokens.accent)
            imageTab.setTextColor(if (selected) tokens.accent else tokens.textTertiary)
        }
        colorTab.setOnClickListener { selectImage(false) }
        imageTab.setOnClickListener { selectImage(true) }
        selectImage(false)

        dialog = MaterialAlertDialogBuilder(context)
            .setView(root)
            .setNegativeButton(R.string.date_console_cancel, null)
            .create()
        dialog.show()
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private fun dpF(value: Float): Float = dp(value).toFloat()
}
