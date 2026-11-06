package com.dsha.sagittarius.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.AppStateEntity
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.databinding.ActivityMainBinding
import com.dsha.sagittarius.agent.ui.ApiKeySheet
import com.dsha.sagittarius.agent.ui.AgentSettingsSheet
import com.dsha.sagittarius.agent.ui.SagittariusActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : ThemedActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: BudgetRepository

    private var latestState: AppStateEntity? = null
    private var pendingShowFloating = false
    private var selectedTab = 0
    private var currentMode: AppMode = AppModeManager.mode.value
    private var lastAgentClickAt = 0L

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshSettingsFragment()
            if (pendingShowFloating && canDrawOverlays()) {
                pendingShowFloating = false
                startFloatingService()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshSettingsFragment()
            if (!it) {
                Toast.makeText(
                    this,
                    R.string.permission_notification_denied,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = (application as SagittariusApp).repository

        setupTabs(savedInstanceState)
        observeServiceState()
        lifecycleScope.launch { repository.ensureInitialState() }
        handleIntent(intent)
        com.dsha.sagittarius.ui.ButtonMotion.attachFab(binding.fabAgent)
        binding.fabAgent.setOnClickListener { openAgent() }
        binding.fabAgent.setOnLongClickListener {
            binding.fabAgent.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            openAgent(voice = true)
            true
        }
        supportFragmentManager.setFragmentResultListener(
            ApiKeySheet.REQUEST_KEY,
            this
        ) { _, result ->
            if (result.getBoolean(ApiKeySheet.RESULT_SUCCESS)) openAgent()
        }
        supportFragmentManager.setFragmentResultListener(
            AgentSettingsSheet.REQUEST_KEY,
            this
        ) { _, result ->
            if (result.getBoolean(AgentSettingsSheet.RESULT_SAVED)) openAgent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshSettingsFragment()
        if (pendingShowFloating && canDrawOverlays()) {
            pendingShowFloating = false
            startFloatingService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun setupTabs(savedInstanceState: Bundle?) {
        val tabs = listOf(
            binding.tabAdd to PageKind.ADD,
            binding.tabHistory to PageKind.HISTORY,
            binding.tabBudget to PageKind.BUDGET,
            binding.tabStats to PageKind.STATS,
            binding.tabDietAdd to PageKind.DIET_ADD,
            binding.tabDietHistory to PageKind.DIET_HISTORY,
            binding.tabNutrition to PageKind.NUTRITION,
            binding.tabWeather to PageKind.WEATHER,
            binding.tabSettings to PageKind.SETTINGS
        )
        tabs.forEach { (view, kind) ->
            view.setOnClickListener { goToPage(kind) }
        }
        val density = resources.displayMetrics.density
        binding.viewPager.setPageTransformer { page, position ->
            val offset = abs(position)
            page.alpha = 1f - offset * 0.12f
            page.translationX = position * 10f * density
        }
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    selectedTab = position
                    updateTabSelection()
                }
            }
        )
        currentMode = AppModeManager.mode.value
        installAdapter(currentMode, savedInstanceState?.getInt(KEY_SELECTED_TAB, 0) ?: 0)
        observeMode()
    }

    private fun installAdapter(mode: AppMode, requestedIndex: Int) {
        val pages = pagesFor(mode)
        binding.viewPager.adapter = TabsAdapter(this, mode)
        binding.viewPager.offscreenPageLimit = 5
        selectedTab = requestedIndex.coerceIn(0, pages.lastIndex)
        binding.viewPager.setCurrentItem(selectedTab, false)
        updateTabVisibility()
        updateTabSelection()
    }

    private fun observeMode() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppModeManager.mode.collect { mode ->
                    if (mode != currentMode) switchMode(mode)
                }
            }
        }
    }

    private fun switchMode(newMode: AppMode) {
        if (newMode == currentMode) return
        val targetKind = targetKind(currentKind(), newMode)
        currentMode = newMode
        binding.viewPager.animate().alpha(0f).setDuration(110L).withEndAction {
            val pages = pagesFor(newMode)
            selectedTab = pages.indexOf(targetKind).coerceAtLeast(0)
            binding.viewPager.adapter = TabsAdapter(this, newMode)
            binding.viewPager.setCurrentItem(selectedTab, false)
            updateTabVisibility()
            updateTabSelection()
            binding.viewPager.animate().alpha(1f).setDuration(180L).start()
        }.start()
    }

    private fun targetKind(current: PageKind, newMode: AppMode): PageKind {
        if (newMode == AppMode.ACCOUNT) {
            return when (current) {
                PageKind.DIET_ADD -> PageKind.ADD
                PageKind.DIET_HISTORY -> PageKind.HISTORY
                PageKind.NUTRITION -> PageKind.STATS
                PageKind.WEATHER -> PageKind.WEATHER
                PageKind.SETTINGS -> PageKind.SETTINGS
                PageKind.BUDGET, PageKind.STATS -> current
                else -> current
            }
        }
        return when (current) {
            PageKind.ADD -> PageKind.DIET_ADD
            PageKind.HISTORY -> PageKind.DIET_HISTORY
            PageKind.BUDGET, PageKind.STATS -> PageKind.NUTRITION
            PageKind.WEATHER -> PageKind.WEATHER
            PageKind.SETTINGS -> PageKind.SETTINGS
            else -> current
        }
    }

    private fun currentKind(): PageKind {
        val pages = pagesFor(currentMode)
        return pages.getOrElse(selectedTab.coerceIn(0, pages.lastIndex)) { pages.first() }
    }

    private fun goToPage(kind: PageKind) {
        val index = pagesFor(currentMode).indexOf(kind)
        if (index >= 0) binding.viewPager.setCurrentItem(index, true)
    }

    private fun updateTabVisibility() {
        val account = currentMode == AppMode.ACCOUNT
        listOf(
            binding.tabAdd to account,
            binding.tabHistory to account,
            binding.tabBudget to account,
            binding.tabStats to account,
            binding.tabDietAdd to !account,
            binding.tabDietHistory to !account,
            binding.tabNutrition to !account,
            binding.tabWeather to true,
            binding.tabSettings to true
        ).forEach { (view, visible) ->
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun updateTabSelection() {
        val pages = pagesFor(currentMode)
        val kind = pages.getOrElse(selectedTab.coerceIn(0, pages.lastIndex)) { pages.first() }
        val all = mapOf(
            PageKind.ADD to binding.tabAdd,
            PageKind.HISTORY to binding.tabHistory,
            PageKind.BUDGET to binding.tabBudget,
            PageKind.STATS to binding.tabStats,
            PageKind.DIET_ADD to binding.tabDietAdd,
            PageKind.DIET_HISTORY to binding.tabDietHistory,
            PageKind.NUTRITION to binding.tabNutrition,
            PageKind.WEATHER to binding.tabWeather,
            PageKind.SETTINGS to binding.tabSettings
        )
        all.forEach { (page, view) -> view.isSelected = page == kind }
    }

    private fun pagesFor(mode: AppMode): List<PageKind> = if (mode == AppMode.ACCOUNT) {
        listOf(PageKind.ADD, PageKind.HISTORY, PageKind.BUDGET, PageKind.STATS, PageKind.WEATHER, PageKind.SETTINGS)
    } else {
        listOf(PageKind.DIET_ADD, PageKind.DIET_HISTORY, PageKind.NUTRITION, PageKind.WEATHER, PageKind.SETTINGS)
    }

    private enum class PageKind { ADD, HISTORY, BUDGET, STATS, DIET_ADD, DIET_HISTORY, NUTRITION, WEATHER, SETTINGS }

    private class TabsAdapter(
        activity: FragmentActivity,
        private val mode: AppMode
    ) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = if (mode == AppMode.ACCOUNT) 6 else 5
        override fun createFragment(position: Int): Fragment = if (mode == AppMode.ACCOUNT) {
            when (position) {
                0 -> AddExpenseFragment()
                1 -> HistoryFragment()
                2 -> BudgetFragment()
                3 -> StatsFragment()
                4 -> WeatherFragment()
                else -> SettingsFragment()
            }
        } else {
            when (position) {
                0 -> DietAddFragment()
                1 -> DietHistoryFragment()
                2 -> NutritionFragment()
                3 -> WeatherFragment()
                else -> SettingsFragment()
            }
        }
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeState().collect { state ->
                    latestState = state
                    val enabled = state?.floatingEnabled == true
                    // 不再进入 App 自动拉起悬浮窗；同时清理“状态为开但服务已不在”的残留状态。
                    if (enabled && !FloatingWindowService.isRunning) {
                        lifecycleScope.launch { repository.setFloatingEnabled(false) }
                    }
                }
            }
        }
    }

    private fun openAgent(voice: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastAgentClickAt < 500L) return
        lastAgentClickAt = now
        lifecycleScope.launch {
            val app = application as SagittariusApp
            val key = withContext(Dispatchers.IO) {
                app.apiKeyStore.load(app.sagittariusPrefs.provider.value)
            }
            if (key.isNullOrBlank()) {
                AgentSettingsSheet().show(supportFragmentManager, "agent_settings")
            } else {
                startActivity(
                    Intent(this@MainActivity, SagittariusActivity::class.java)
                        .putExtra(SagittariusActivity.EXTRA_VOICE, voice)
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val tab = intent.getIntExtra(EXTRA_TAB, -1)
        val pages = pagesFor(currentMode)
        if (tab in pages.indices) {
            if (tab != selectedTab) {
                binding.viewPager.setCurrentItem(tab, false)
                binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            intent.removeExtra(EXTRA_TAB)
            intent.removeExtra(EXTRA_TAB_NAME)
        }
        if (intent.getBooleanExtra(EXTRA_SHOW_FLOATING, false)) {
            intent.removeExtra(EXTRA_SHOW_FLOATING)
            binding.root.post { requestFloatingWindow() }
        }
    }

    fun requestFloatingWindow() {
        if (!canDrawOverlays()) {
            pendingShowFloating = true
            refreshSettingsFragment()
            showOverlayPermissionGuide()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission()
        }
        startFloatingService()
    }

    fun stopFloatingService() {
        stopService(Intent(this, FloatingWindowService::class.java))
        lifecycleScope.launch { repository.setFloatingEnabled(false) }
        refreshSettingsFragment()
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Toast.makeText(this, "当前系统无需申请通知权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    fun showOverlayPermissionGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.overlay_guide_dialog_title)
            .setMessage(R.string.overlay_guide_dialog_body)
            .setNegativeButton(R.string.cancel) { _, _ -> pendingShowFloating = false }
            .setPositiveButton(R.string.go_settings) { _, _ -> openOverlaySettings() }
            .show()
    }

    fun showHyperOsGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hyperos_dialog_title)
            .setMessage(R.string.hyperos_dialog_body)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    fun showWidgetGuide() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_dialog_title)
            .setMessage(R.string.widget_dialog_body)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            overlaySettingsLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "未找到悬浮窗设置页，请前往：设置 → 应用管理 → 权限管理 → 悬浮窗",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_SHOW
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "启动悬浮窗失败：${e.message ?: "系统限制前台服务"}",
                Toast.LENGTH_LONG
            ).show()
        }
        refreshSettingsFragment()
    }

    private fun refreshSettingsFragment() {
        supportFragmentManager.fragments
            .filterIsInstance<SettingsFragment>()
            .firstOrNull()
            ?.refreshPermissions()
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    companion object {
        const val EXTRA_SHOW_FLOATING = "com.dsha.sagittarius.extra.SHOW_FLOATING"
        const val EXTRA_TAB = "com.dsha.sagittarius.extra.TAB"
        const val EXTRA_TAB_NAME = "com.dsha.sagittarius.extra.TAB_NAME"

        const val TAB_ADD = 0
        const val TAB_HISTORY = 1
        const val TAB_BUDGET = 2
        const val TAB_STATS = 3
        const val TAB_WEATHER = 4
        const val TAB_SETTINGS = 5

        private const val KEY_SELECTED_TAB = "selected_tab"
    }
}
