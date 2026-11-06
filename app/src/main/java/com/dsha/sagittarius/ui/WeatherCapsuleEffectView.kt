package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.dsha.sagittarius.data.weather.WeatherType
import com.dsha.sagittarius.theme.ThemeManager
import kotlin.math.min

/**
 * 悬浮窗与天气页预览用的迷你灵动岛特效层。只绘制天气粒子，不处理触摸。
 */
class WeatherCapsuleEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val renderer = WeatherEffectRenderer(
        density,
        ThemeManager.isLight,
        !MotionUtils.isLowRam(context)
    )
    private val clipPath = Path()
    private val rect = RectF()
    private var weather: WeatherType? = null
    private var enabled = false
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12_000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setWeather(type: WeatherType?, effectsEnabled: Boolean) {
        weather = type
        enabled = effectsEnabled
        updateAnimation()
        invalidate()
    }

    private fun updateAnimation() {
        val shouldAnimate = enabled && weather != null && isAttachedToWindow && isShown &&
            MotionUtils.ambientAllowed(context)
        if (shouldAnimate && !animator.isRunning) {
            animator.start()
        } else if (!shouldAnimate && animator.isRunning) {
            animator.cancel()
            phase = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val type = weather ?: return
        if (!enabled || width <= 0 || height <= 0) return
        val radius = min(dp(18f), height / 2f)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        renderer.draw(
            canvas,
            rect,
            radius,
            type,
            if (MotionUtils.ambientAllowed(context)) phase * 12f else 0f,
            compact = true,
            reducedMotion = !MotionUtils.ambientAllowed(context),
            lowQuality = MotionUtils.isLowRam(context)
        )
        canvas.restoreToCount(save)
    }

    private fun dp(value: Float): Float = value * density
}
