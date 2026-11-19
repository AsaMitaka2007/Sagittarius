package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.theme.ThemeManager
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 入口扩散转场。
 *
 * 从被点击按钮的圆心向外扩张一个圆形可视区，遮罩在圆外保持主题底色；
 * 圆的边缘叠加引力场粒子与多层光晕，表现“边缘把页面推开”的力。
 * 减少动画/低内存设备退化为整页淡入。
 */
class AgentEntryRevealView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val accent = ButtonAccentManager.current
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
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
    private var onEnd: (() -> Unit)? = null
    private var animator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun start(originX: Float, originY: Float, startRadius: Float, onEnd: (() -> Unit)? = null) {
        this.originX = originX
        this.originY = originY
        this.startRadius = startRadius
        this.onEnd = onEnd
        maxRadius = hypot(
            max(originX, width - originX).toDouble(),
            max(originY, height - originY).toDouble()
        ).toFloat() + dp(24f)
        particles.clear()
        if (MotionUtils.ambientAllowed(context)) {
            repeat(90) { particles += spawnParticle() }
        }
        running = true
        visibility = VISIBLE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (particles.isEmpty()) 260L else 760L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    running = false
                    visibility = GONE
                    onEnd?.invoke()
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
        val scrimAlpha = (255 * (1f - progress).coerceIn(0f, 1f)).toInt()
        val scrimColor = if (ThemeManager.isLight) 0xFFF6F7FB.toInt() else 0xFF0B1220.toInt()
        scrimPaint.color = scrimColor
        scrimPaint.alpha = scrimAlpha

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawCircle(originX, originY, radius, holePaint)
        canvas.restoreToCount(layer)

        drawEdge(canvas, radius)
        drawParticles(canvas, radius, scrimAlpha)
    }

    private fun drawEdge(canvas: Canvas, radius: Float) {
        val fade = sin(progress * Math.PI).toFloat().coerceIn(0f, 1f)
        ringPaint.strokeWidth = 2.4f * density
        ringPaint.color = ColorUtils.setAlphaComponent(colors[2], (120 * fade).toInt())
        canvas.drawCircle(originX, originY, radius, ringPaint)
        ringPaint.strokeWidth = 7f * density
        ringPaint.color = ColorUtils.setAlphaComponent(colors[0], (34 * fade).toInt())
        canvas.drawCircle(originX, originY, radius - 3f * density, ringPaint)
        val glow = RadialGradient(
            originX, originY, (radius + 26f * density).coerceAtLeast(1f),
            intArrayOf(
                Color.TRANSPARENT,
                ColorUtils.setAlphaComponent(colors[1], (30 * fade).toInt()),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.86f, 0.94f, 1f),
            Shader.TileMode.CLAMP
        )
        ringPaint.shader = glow
        ringPaint.strokeWidth = 28f * density
        canvas.drawCircle(originX, originY, radius, ringPaint)
        ringPaint.shader = null
    }

    private fun drawParticles(canvas: Canvas, radius: Float, scrimAlpha: Int) {
        if (particles.isEmpty()) return
        val fade = sin(progress * Math.PI).toFloat().coerceIn(0f, 1f) *
            (scrimAlpha / 255f).coerceIn(0f, 1f)
        if (fade <= 0.01f) return
        particles.forEach { p ->
            val angle = p.angle + progress * p.spin
            val wobble = sin(progress * 9f + p.phase) * p.wobble * density
            val distance = radius + p.offset * density + wobble + progress * 12f * density
            val x = originX + cos(angle) * distance
            val y = originY + sin(angle) * distance
            val alpha = (p.alpha * fade).toInt().coerceIn(0, 255)
            particlePaint.color = ColorUtils.setAlphaComponent(colors[p.colorIndex], alpha)
            canvas.drawCircle(x, y, p.size * density, particlePaint)
            val trail = (6f + p.offset * 0.35f) * density
            trailPaint.color = ColorUtils.setAlphaComponent(colors[p.colorIndex], (alpha * 0.45f).toInt())
            canvas.drawLine(
                originX + cos(angle) * (distance - trail),
                originY + sin(angle) * (distance - trail),
                x, y, trailPaint
            )
        }
        // 重新生成少量粒子，保持边缘持续有“被推开”的颗粒感
        if (particles.size < 90 && progress < 0.85f) {
            repeat(2) { particles += spawnParticle() }
        }
    }

    private fun spawnParticle(): Particle = Particle(
        angle = random.nextFloat() * (Math.PI * 2).toFloat(),
        spin = (random.nextFloat() - 0.5f) * 1.6f,
        offset = random.nextFloat() * 18f,
        wobble = 2f + random.nextFloat() * 8f,
        phase = random.nextFloat() * 6.28f,
        size = 0.8f + random.nextFloat() * 2.1f,
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
