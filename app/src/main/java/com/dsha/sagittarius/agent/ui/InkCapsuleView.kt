package com.dsha.sagittarius.agent.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/** 纯墨水黑圆角容器，不画描边、发光或渐变。 */
class InkCapsuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val backgroundDrawable = GradientDrawable().apply {
        setColor(AgentInkColors.INK)
    }

    var cornerRadius: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            backgroundDrawable.cornerRadius = field
            invalidateOutline()
        }

    init {
        clipToOutline = true
        elevation = 16f * resources.displayMetrics.density
        background = backgroundDrawable
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0, 0, view.width, view.height,
                    cornerRadius.coerceAtMost(minOf(view.width, view.height) / 2f)
                )
            }
        }
    }
}
