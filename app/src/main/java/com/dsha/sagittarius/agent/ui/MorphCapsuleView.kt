package com.dsha.sagittarius.agent.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.dsha.sagittarius.ui.ThemeTokens

/**
 * 可形变的胶囊容器：从药丸形态过渡到卡片，内容随圆角裁切。
 * 圆角半径由外部逐帧驱动，配合 ValueAnimator 形成灵动岛式展开。
 */
class MorphCapsuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val backgroundDrawable = GradientDrawable()
    private var fillColor = 0
    private var strokeColor = 0

    var cornerRadius: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            backgroundDrawable.cornerRadius = field
            invalidateOutline()
        }

    init {
        clipToOutline = true
        elevation = 18f * resources.displayMetrics.density
        background = backgroundDrawable
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    cornerRadius.coerceAtMost(minOf(view.width, view.height) / 2f)
                )
            }
        }
    }

    fun applyTheme(tokens: ThemeTokens) {
        fillColor = tokens.cardFillTop
        strokeColor = tokens.cardStrokeStart
        backgroundDrawable.setColor(fillColor)
        backgroundDrawable.setStroke((1f * resources.displayMetrics.density).toInt(), strokeColor)
    }
}
