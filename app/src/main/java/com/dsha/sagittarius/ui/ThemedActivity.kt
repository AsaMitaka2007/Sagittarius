package com.dsha.sagittarius.ui

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * 所有界面 Activity 的基类：启动前套用 DataStore 主题，主题切换时立即 recreate。
 * Android 12+ 毛玻璃主题额外给窗口开启背景模糊。
 */
abstract class ThemedActivity : AppCompatActivity() {

    private var appliedMode: ThemeMode = ThemeManager.current

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedMode = ThemeManager.current
        setTheme(themeResFor(appliedMode))
        super.onCreate(savedInstanceState)
        applyGlassBlurIfNeeded()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ThemeManager.mode.collect { mode ->
                    if (mode != appliedMode) {
                        appliedMode = mode
                        if (followsThemeChanges()) recreate()
                    }
                }
            }
        }
    }

    /** 子类可强制固定主题（例如 Agent 页始终墨水黑）。 */
    protected open fun themeResFor(mode: ThemeMode): Int = mode.themeRes

    /** Agent 页不随全局主题重建。 */
    protected open fun followsThemeChanges(): Boolean = true

    protected open fun allowsGlassBlur(): Boolean = false

    private fun applyGlassBlurIfNeeded() {
        if (!allowsGlassBlur()) return
        if (appliedMode != ThemeMode.GLASS) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setBackgroundBlurRadius((42f * resources.displayMetrics.density).toInt())
            window.setDimAmount(0f)
        }
    }
}
