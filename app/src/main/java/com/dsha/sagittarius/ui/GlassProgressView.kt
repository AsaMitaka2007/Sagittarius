package com.dsha.sagittarius.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils

/**
 * 轻量迷你进度条：不依赖 ProgressBar 的 tint 限制，可以同时平滑改变进度和渐变颜色。
 */
class GlassProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var visualProgress = 0f
    private var targetProgress = 0f
    private var startColor = tokens.gradientStart
    private var endColor = tokens.gradientMid
    private var animator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()

    init {
        trackPaint.color = tokens.progressTrack
        highlightPaint.color = ColorUtils.setAlphaComponent(tokens.textPrimary, 60)
    }

    fun setProgress(value: Float, animate: Boolean = true) {
        val clamped = value.coerceIn(0f, 1f)
        targetProgress = clamped
        if (!animate || MotionUtils.reduceMotion(context)) {
            visualProgress = clamped
            invalidate()
            return
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(visualProgress, clamped).apply {
            duration = 480L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                visualProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    fun setPalette(primary: Int, secondary: Int, animate: Boolean = true) {
        if (!animate || MotionUtils.reduceMotion(context)) {
            colorAnimator?.cancel()
            startColor = primary
            endColor = secondary
            invalidate()
            return
        }
        if (startColor == primary && endColor == secondary) return
        val fromStart = startColor
        val fromEnd = endColor
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                startColor = argbEvaluator.evaluate(t, fromStart, primary) as Int
                endColor = argbEvaluator.evaluate(t, fromEnd, secondary) as Int
                invalidate()
            }
        }
        colorAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val radius = h / 2f
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        val fillWidth = (width * visualProgress).coerceAtLeast(if (visualProgress > 0f) h else 0f)
        if (fillWidth <= 0f) return
        rect.set(0f, 0f, fillWidth.coerceAtMost(width.toFloat()), h)
        fillPaint.shader = LinearGradient(
            0f, 0f, fillWidth, 0f,
            startColor, endColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        highlightPaint.strokeWidth = 1f * density
        highlightPaint.style = Paint.Style.STROKE
        canvas.drawLine(
            radius, 1.2f * density,
            (fillWidth - radius).coerceAtLeast(radius), 1.2f * density,
            highlightPaint
        )
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
