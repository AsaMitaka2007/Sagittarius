package com.dsha.sagittarius.agent.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.ui.MotionUtils
import com.dsha.sagittarius.ui.ThemeTokens

/** 与悬浮窗一致的 Q 弹下拉菜单。 */
class AnimatedMenuPopup(
    private val anchor: View,
    private val items: List<String>,
    private val selectedIndex: Int,
    private val onPick: (Int) -> Unit
) {
    private val context: Context = anchor.context
    private val tokens = ThemeTokens.of(context)
    private val density = context.resources.displayMetrics.density
    private val popup: PopupWindow

    init {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(tokens.cardFillTop)
                setStroke(dp(1), tokens.cardStrokeStart)
            }
            elevation = dp(12).toFloat()
        }
        items.forEachIndexed { index, label ->
            val row = TextView(context).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                setTextColor(if (index == selectedIndex) tokens.accent else tokens.textPrimary)
                background = if (index == selectedIndex) {
                    GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, 28))
                    }
                } else null
                setOnClickListener {
                    dismiss()
                    onPick(index)
                }
            }
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }
        popup = PopupWindow(
            content,
            anchor.width.coerceAtLeast(dp(180)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(null)
            elevation = dp(14).toFloat()
        }
    }

    fun show() {
        if (!anchor.isAttachedToWindow) return
        popup.showAsDropDown(anchor, 0, dp(6))
        val content = popup.contentView
        if (MotionUtils.reduceMotion(context)) {
            content.alpha = 1f
            return
        }
        content.alpha = 0f
        content.scaleX = 0.88f
        content.scaleY = 0.82f
        content.pivotX = content.width / 2f
        content.pivotY = 0f
        SpringAnimation(content, DynamicAnimation.SCALE_X, 1f).apply {
            spring.dampingRatio = 0.48f
            spring.stiffness = 420f
            start()
        }
        SpringAnimation(content, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = 0.48f
            spring.stiffness = 420f
            start()
        }
        ObjectAnimator.ofFloat(content, View.ALPHA, 0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun dismiss() {
        if (!popup.isShowing) return
        val content = popup.contentView
        if (MotionUtils.reduceMotion(context)) {
            popup.dismiss()
            return
        }
        content.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(110L)
            .withEndAction { popup.dismiss() }
            .start()
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
