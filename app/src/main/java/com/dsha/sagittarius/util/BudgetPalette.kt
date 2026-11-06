package com.dsha.sagittarius.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils

data class GlassPalette(
    val primary: Int,
    val secondary: Int,
    val display: Int,
    val glow: Int
)

enum class WidgetTier { GREEN, BLUE, ORANGE, RED, DEEP_RED }

/**
 * 颜色阈值也只在这里定义一次。悬浮窗、主界面、历史摘要和小部件共用。
 * ratio = 剩余金额 / 预算金额。
 */
object BudgetPalette {

    private val teal = Color.parseColor("#34E7C4")
    private val blue = Color.parseColor("#3B82F6")
    private val purple = Color.parseColor("#8B5CF6")
    private val yellow = Color.parseColor("#FBBF24")
    private val orange = Color.parseColor("#FB923C")
    private val red = Color.parseColor("#EF4444")
    private val deepRed = Color.parseColor("#991B1B")
    private val deepRedLight = Color.parseColor("#FCA5A5")

    fun forRatio(ratio: Double?, lightTheme: Boolean = false): GlassPalette {
        if (ratio == null) {
            return palette(teal, blue, lightTheme)
        }
        if (ratio <= 0.0) {
            return GlassPalette(
                primary = deepRed,
                secondary = Color.parseColor("#450A0A"),
                display = if (lightTheme) Color.parseColor("#991B1B") else deepRedLight,
                glow = ColorUtils.setAlphaComponent(Color.parseColor("#EF4444"), 110)
            )
        }
        val (start, end, t) = when {
            ratio >= 0.70 -> Triple(teal, blue, ((ratio - 0.70) / 0.30).toFloat())
            ratio >= 0.40 -> Triple(blue, purple, ((ratio - 0.40) / 0.30).toFloat())
            ratio >= 0.20 -> Triple(yellow, orange, ((ratio - 0.20) / 0.20).toFloat())
            else -> Triple(orange, red, (ratio / 0.20).toFloat())
        }
        val primary = ColorUtils.blendARGB(start, end, t.coerceIn(0f, 1f))
        val secondary = ColorUtils.blendARGB(end, start, 0.36f)
        return palette(primary, secondary, lightTheme)
    }

    fun widgetTier(ratio: Double?): WidgetTier = when {
        ratio == null -> WidgetTier.BLUE
        ratio <= 0.0 -> WidgetTier.DEEP_RED
        ratio < 0.20 -> WidgetTier.RED
        ratio < 0.40 -> WidgetTier.ORANGE
        ratio < 0.70 -> WidgetTier.BLUE
        else -> WidgetTier.GREEN
    }

    fun widgetLabel(tier: WidgetTier): String = when (tier) {
        WidgetTier.GREEN -> "青绿"
        WidgetTier.BLUE -> "蓝"
        WidgetTier.ORANGE -> "橙"
        WidgetTier.RED -> "红"
        WidgetTier.DEEP_RED -> "深红"
    }

    private fun palette(primary: Int, secondary: Int, lightTheme: Boolean): GlassPalette {
        val display = if (lightTheme) {
            ColorUtils.blendARGB(primary, Color.BLACK, 0.42f)
        } else {
            ColorUtils.blendARGB(primary, Color.WHITE, 0.42f)
        }
        val glow = ColorUtils.setAlphaComponent(primary, 120)
        return GlassPalette(primary, secondary, display, glow)
    }
}
