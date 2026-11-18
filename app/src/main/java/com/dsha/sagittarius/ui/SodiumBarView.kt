package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.min

/** 钠摄入进度条：超过目标后用警示色显示。 */
class SodiumBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        color = tokens.textSecondary
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        textAlign = Paint.Align.RIGHT
        color = tokens.textPrimary
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        textAlign = Paint.Align.RIGHT
        color = tokens.textTertiary
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var sodium = 0.0
    private var goal = 2000.0
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setData(sodium: Double, goal: Double, animate: Boolean = true) {
        this.sodium = sodium
        this.goal = goal
        animator?.cancel()
        if (!animate) {
            progress = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ratio = ratio()
        val over = goal > 0.0 && sodium > goal
        labelPaint.color = tokens.textSecondary
        canvas.drawText("钠", dp(2f), dp(14f), labelPaint)
        valuePaint.color = tokens.textPrimary
        canvas.drawText(
            String.format(Locale.CHINA, "%.0f / %.0f mg", sodium, goal),
            width - dp(2f), dp(14f), valuePaint
        )

        val top = dp(24f)
        val barHeight = dp(8f)
        rect.set(dp(2f), top, width - dp(2f), top + barHeight)
        trackPaint.color = ColorUtils.setAlphaComponent(tokens.textTertiary, 42)
        canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, trackPaint)

        val shown = (ratio.coerceIn(0.0, 1.0) * progress).toFloat()
        if (shown > 0f) {
            val fillWidth = min(rect.width(), rect.width() * shown)
            rect.right = rect.left + fillWidth.coerceAtLeast(barHeight)
            fillPaint.color = if (over) tokens.warning else tokens.accent
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, fillPaint)
        }

        hintPaint.color = if (over) tokens.warning else tokens.textTertiary
        val percent = (ratio * 100).toInt()
        val hint = if (over) "已超过参考上限 $percent%" else "占参考上限 $percent%"
        canvas.drawText(hint, width - dp(2f), dp(52f), hintPaint)
    }

    private fun ratio(): Double =
        if (goal <= 0.0) 0.0 else (sodium / goal).coerceAtLeast(0.0)

    private fun dp(value: Float): Float = value * density
}
