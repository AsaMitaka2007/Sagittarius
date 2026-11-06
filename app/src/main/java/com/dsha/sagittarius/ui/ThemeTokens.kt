package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.dsha.sagittarius.R

/**
 * 从当前 Theme 解析设计 token。所有自定义 View 只从这里取颜色，
 * 页面 XML 通过 ?attr/bl... 引用同一套 token。
 */
class ThemeTokens private constructor(
    @ColorInt val textPrimary: Int,
    @ColorInt val textSecondary: Int,
    @ColorInt val textTertiary: Int,
    @ColorInt val divider: Int,
    @ColorInt val accent: Int,
    @ColorInt val success: Int,
    @ColorInt val warning: Int,
    @ColorInt val warningText: Int,
    @ColorInt val gradientStart: Int,
    @ColorInt val gradientMid: Int,
    @ColorInt val gradientEnd: Int,
    @ColorInt val titleStart: Int,
    @ColorInt val titleMid: Int,
    @ColorInt val titleEnd: Int,
    @ColorInt val titleStrokeStart: Int,
    @ColorInt val titleStrokeMid: Int,
    @ColorInt val titleStrokeEnd: Int,
    @ColorInt val titleShadow: Int,
    @ColorInt val cardFillTop: Int,
    @ColorInt val cardFillBottom: Int,
    @ColorInt val cardStrokeStart: Int,
    @ColorInt val cardStrokeMid: Int,
    @ColorInt val cardStrokeEnd: Int,
    @ColorInt val cardHighlight: Int,
    @ColorInt val cardShadow: Int,
    @ColorInt val buttonStart: Int,
    @ColorInt val buttonMid: Int,
    @ColorInt val buttonEnd: Int,
    @ColorInt val buttonText: Int,
    @ColorInt val buttonDisabledText: Int,
    @ColorInt val flowStart: Int,
    @ColorInt val flowMid: Int,
    @ColorInt val flowEnd: Int,
    @ColorInt val softStart: Int,
    @ColorInt val softMid: Int,
    @ColorInt val softEnd: Int,
    @ColorInt val floatingButtonFill: Int,
    @ColorInt val floatingButtonStroke: Int,
    @ColorInt val progressTrack: Int,
    @ColorInt val chipSelectedText: Int,
    @ColorInt val chipUnselectedFill: Int,
    @ColorInt val chipUnselectedStroke: Int,
    @ColorInt val chipUnselectedText: Int,
    @ColorInt val tabInactive: Int,
    @ColorInt val inputFill: Int,
    @ColorInt val inputStroke: Int,
    @ColorInt val metricTile: Int,
    @ColorInt val iconCircle: Int,
    @ColorInt val iconButton: Int,
    @ColorInt val dialogSurface: Int,
    @ColorInt val spinnerPopup: Int,
    @ColorInt val nodeWarning: Int,
    @ColorInt val nodeNear: Int,
    @ColorInt val nodeBarAllocation: Int,
    @ColorInt val nodeBarUsed: Int
) {
    companion object {
        fun of(context: Context): ThemeTokens {
            val attrs = intArrayOf(
                R.attr.blTextPrimary, R.attr.blTextSecondary, R.attr.blTextTertiary,
                R.attr.blDivider, R.attr.blAccent, R.attr.blSuccess, R.attr.blWarning,
                R.attr.blWarningText, R.attr.blGradientStart, R.attr.blGradientMid,
                R.attr.blGradientEnd, R.attr.blTitleStart, R.attr.blTitleMid,
                R.attr.blTitleEnd, R.attr.blTitleStrokeStart, R.attr.blTitleStrokeMid,
                R.attr.blTitleStrokeEnd, R.attr.blTitleShadow, R.attr.blCardFillTop,
                R.attr.blCardFillBottom, R.attr.blCardStrokeStart, R.attr.blCardStrokeMid,
                R.attr.blCardStrokeEnd, R.attr.blCardHighlight, R.attr.blCardShadow,
                R.attr.blButtonStart, R.attr.blButtonMid, R.attr.blButtonEnd,
                R.attr.blButtonText, R.attr.blButtonDisabledText, R.attr.blFlowStart,
                R.attr.blFlowMid, R.attr.blFlowEnd, R.attr.blSoftStart, R.attr.blSoftMid,
                R.attr.blSoftEnd, R.attr.blFloatingButtonFill, R.attr.blFloatingButtonStroke,
                R.attr.blProgressTrack, R.attr.blChipSelectedText,
                R.attr.blChipUnselectedFill, R.attr.blChipUnselectedStroke,
                R.attr.blChipUnselectedText, R.attr.blTabInactive, R.attr.blInputFill,
                R.attr.blInputStroke, R.attr.blMetricTile, R.attr.blIconCircle,
                R.attr.blIconButton, R.attr.blDialogSurface, R.attr.blSpinnerPopup,
                R.attr.blNodeWarning, R.attr.blNodeNear, R.attr.blNodeBarAllocation,
                R.attr.blNodeBarUsed
            )
            val a = context.obtainStyledAttributes(attrs)
            fun c(index: Int, fallback: Int): Int = a.getColor(index, fallback)
            val tokens = ThemeTokens(
                c(0, Color.WHITE), c(1, 0xFF9AA8BD.toInt()), c(2, 0xFF64748B.toInt()),
                c(3, 0x1FFFFFFF), c(4, 0xFF34E7C4.toInt()), c(5, 0xFF22C55E.toInt()),
                c(6, 0xFFEF4444.toInt()), c(7, 0xFFFECACA.toInt()),
                c(8, 0xFF43E6C6.toInt()), c(9, 0xFF6C7CFB.toInt()), c(10, 0xFFEFA9CC.toInt()),
                c(11, 0xFF43E6C6.toInt()), c(12, 0xFFB68BFF.toInt()), c(13, 0xFFEFA9CC.toInt()),
                c(14, 0xFFA6FFF0.toInt()), c(15, 0xFFB9C1FF.toInt()), c(16, 0xFFFFD5E8.toInt()),
                c(17, 0x60000000), c(18, 0xD91A2438.toInt()), c(19, 0xB30D1524.toInt()),
                c(20, 0x6043E6C6), c(21, 0x4E6C7CFB), c(22, 0x40EFA9CC),
                c(23, 0x1AFFFFFF), c(24, 0x2A000000), c(25, 0xFF43E6C6.toInt()),
                c(26, 0xFF6C7CFB.toInt()), c(27, 0xFFEFA9CC.toInt()), c(28, 0xFF06131B.toInt()),
                c(29, 0x82FFFFFF.toInt()), c(30, 0xFFA6FFF0.toInt()), c(31, 0xFFB9C1FF.toInt()),
                c(32, 0xFFFFD5E8.toInt()), c(33, 0x3343E6C6), c(34, 0x296C7CFB),
                c(35, 0x22EFA9CC), c(36, 0x24FFFFFF), c(37, 0x33FFFFFF),
                c(38, 0x1CFFFFFF), c(39, Color.WHITE), c(40, 0x12FFFFFF),
                c(41, 0x2DFFFFFF), c(42, 0xFFB7C2D4.toInt()), c(43, 0xFF7F8A9F.toInt()),
                c(44, 0x16FFFFFF), c(45, 0x2BFFFFFF), c(46, 0x0FFFFFFF),
                c(47, 0x24FFFFFF), c(48, 0x16FFFFFF), c(49, 0xFF182134.toInt()),
                c(50, 0xFF182134.toInt()), c(51, 0xFFEF4444.toInt()), c(52, 0xFFF59E0B.toInt()),
                c(53, 0xFF43E6C6.toInt()), c(54, 0xFFEFA9CC.toInt())
            )
            a.recycle()
            return tokens
        }
    }
}

fun Context.blColor(@AttrRes attr: Int, @ColorInt fallback: Int): Int {
    val a = obtainStyledAttributes(intArrayOf(attr))
    val color = a.getColor(0, fallback)
    a.recycle()
    return color
}
