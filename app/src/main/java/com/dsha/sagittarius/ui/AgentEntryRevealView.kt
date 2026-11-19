package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 入口“铺满”转场：从按钮圆心向外扩散一整块不透明色域，
 * 边缘带引力场粒子；铺满后回调 onFilled，页面再淡入，最后覆盖层淡出。
 */
class AgentEntryRevealView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val accent = ButtonAccentManager.current
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
    }
    private val colors = intArrayOf(
        accent.startColor,
        accent.endColor,
        Color.parseColor("#7BF7FF"),
        Color.parseColor("#FFFFFF")
    )
    private val random = Random(System.nanoTime())
    private val particles = ArrayList<Particle>()
    private var originX = 0f
    private var originY = 0f
    private var startRadius = 0f
    private var maxRadius = 0f
    private var progress = 0f
    private var running = false
    private var filledNotified = false
    private var onFilled: (() -> Unit)? = null
    private var animator: ValueAnimator? = null
    private var fadeAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start(originX: Float, originY: Float, startRadius: Float, onFilled: (() -> Unit)? = null) {
        this.originX = originX
        this.originY = originY
        this.startRadius = startRadius
        this.onFilled = onFilled
        filledNotified = false
        maxRadius = hypot(
            max(originX, width - originX).toDouble(),
            max(originY, height - originY).toDouble()
        ).toFloat() + dp(32f)
        particles.clear()
        if (MotionUtils.ambientAllowed(context)) {
            repeat(110) { particles += spawnParticle() }
        }
        running = true
        alpha = 1f
        visibility = VISIBLE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (particles.isEmpty()) 340L else 660L
            interpolator = DecelerateInterpolator(1.15f)
            addUpdateListener {
                progress = it.animatedValue as Float
                if (progress >= 0.995f && !filledNotified) {
                    filledNotified = true
                    onFilled?.invoke()
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!filledNotified) {
                        filledNotified = true
                        onFilled?.invoke()
                    }
                    fadeOut()
                }
            })
            start()
        }
    }

    private fun fadeOut() {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 240L
            addUpdateListener {
                alpha = it.animatedValue as Float
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    running = false
                    visibility = GONE
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running) return
        val eased = 1f - (1f - progress) * (1f - progress)
        val radius = startRadius + (maxRadius - startRadius) * eased
        // 统一使用深色墨迹覆盖，浅色主题下也能看到明确的“铺满”过程
        val baseColor = 0xFF0B1220.toInt()
        fillPaint.shader = RadialGradient(
            originX, originY, radius.coerceAtLeast(1f),
            intArrayOf(
                baseColor,
                baseColor,
                ColorUtils.setAlphaComponent(accent.startColor, 40),
                ColorUtils.setAlphaComponent(accent.endColor, 90)
            ),
            floatArrayOf(0f, 0.78f, 0.94f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(originX, originY, radius, fillPaint)
        fillPaint.shader = null
        drawEdge(canvas, radius)
        drawParticles(canvas, radius)
    }

    private fun drawEdge(canvas: Canvas, radius: Float) {
        val fade = sin(progress * Math.PI).toFloat().coerceIn(0f, 1f)
        ringPaint.strokeWidth = 2.4f * density
        ringPaint.color = ColorUtils.setAlphaComponent(colors[2], (130 * fade).toInt())
        canvas.drawCircle(originX, originY, radius, ringPaint)
        ringPaint.strokeWidth = 9f * density
        ringPaint.color = ColorUtils.setAlphaComponent(colors[0], (38 * fade).toInt())
        canvas.drawCircle(originX, originY, radius - 4f * density, ringPaint)
    }

    private fun drawParticles(canvas: Canvas, radius: Float) {
        if (particles.isEmpty()) return
        val fade = sin(progress * Math.PI).toFloat().coerceIn(0f, 1f)
        if (fade <= 0.01f) return
        particles.forEach { p ->
            val angle = p.angle + progress * p.spin
            val wobble = sin(progress * 9f + p.phase) * p.wobble * density
            val distance = radius + p.offset * density + wobble + progress * 14f * density
            val x = originX + cos(angle) * distance
            val y = originY + sin(angle) * distance
            val alpha = (p.alpha * fade).toInt().coerceIn(0, 255)
            particlePaint.color = ColorUtils.setAlphaComponent(colors[p.colorIndex], alpha)
            canvas.drawCircle(x, y, p.size * density, particlePaint)
            val trail = (6f + p.offset * 0.35f) * density
            trailPaint.color = ColorUtils.setAlphaComponent(colors[p.colorIndex], (alpha * 0.5f).toInt())
            canvas.drawLine(
                originX + cos(angle) * (distance - trail),
                originY + sin(angle) * (distance - trail),
                x, y, trailPaint
            )
        }
    }

    private fun spawnParticle(): Particle = Particle(
        angle = random.nextFloat() * (Math.PI * 2).toFloat(),
        spin = (random.nextFloat() - 0.5f) * 1.5f,
        offset = random.nextFloat() * 20f,
        wobble = 2f + random.nextFloat() * 9f,
        phase = random.nextFloat() * 6.28f,
        size = 0.8f + random.nextFloat() * 2.2f,
        alpha = 110 + random.nextInt(145),
        colorIndex = random.nextInt(colors.size)
    )

    private fun dp(value: Float): Float = value * density

    private data class Particle(
        val angle: Float,
        val spin: Float,
        val offset: Float,
        val wobble: Float,
        val phase: Float,
        val size: Float,
        val alpha: Int,
        val colorIndex: Int
    )
}
