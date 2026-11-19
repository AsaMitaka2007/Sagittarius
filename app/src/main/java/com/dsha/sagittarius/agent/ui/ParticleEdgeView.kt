package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.ButtonAccentManager
import com.dsha.sagittarius.ui.MotionUtils
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/**
 * 胶囊边缘粒子层：围绕当前胶囊圆角矩形向外喷出粒子，
 * 与首页入口扩散共用同一套视觉语言。
 */
class ParticleEdgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val accent = ButtonAccentManager.current
    private val colors = intArrayOf(
        if (accent.isTransparent) Color.WHITE else accent.startColor,
        if (accent.isTransparent) Color.WHITE else accent.endColor,
        Color.parseColor("#7BF7FF"),
        Color.WHITE
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
    }
    private val path = Path()
    private val measure = PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)
    private val random = Random(System.nanoTime())
    private val particles = ArrayList<Particle>()
    private var bounds = RectF()
    private var radius = 0f
    private var progress = 0f
    private var animator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        alpha = 0f
    }

    fun updateBounds(rect: RectF, cornerRadius: Float) {
        bounds.set(rect)
        radius = cornerRadius
        invalidate()
    }

    fun burst() {
        if (!MotionUtils.ambientAllowed(context)) {
            alpha = 0f
            return
        }
        particles.clear()
        repeat(70) { particles += spawn() }
        alpha = 1f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560L
            interpolator = DecelerateInterpolator(0.9f)
            addUpdateListener {
                progress = it.animatedValue as Float
                alpha = (1f - progress).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty() || bounds.width() <= 0f) return
        path.reset()
        path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        measure.setPath(path, false)
        val length = measure.length
        if (length <= 0f) return
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        particles.forEach { p ->
            measure.getPosTan(p.t * length, pos, tan)
            var nx = -tan[1]
            var ny = tan[0]
            val len = hypot(nx.toDouble(), ny.toDouble()).toFloat().coerceAtLeast(0.001f)
            nx /= len
            ny /= len
            if ((pos[0] - cx) * nx + (pos[1] - cy) * ny < 0f) {
                nx = -nx
                ny = -ny
            }
            val distance = (p.offset + progress * p.speed) * density
            val x = pos[0] + nx * distance
            val y = pos[1] + ny * distance
            val a = (p.alpha * (1f - progress)).toInt().coerceIn(0, 255)
            paint.color = ColorUtils.setAlphaComponent(colors[p.colorIndex], a)
            canvas.drawCircle(x, y, p.size * density, paint)
            trailPaint.color = ColorUtils.setAlphaComponent(colors[p.colorIndex], (a * 0.45f).toInt())
            canvas.drawLine(
                pos[0] + nx * max(0f, distance - 8f * density),
                pos[1] + ny * max(0f, distance - 8f * density),
                x, y, trailPaint
            )
        }
    }

    private fun spawn(): Particle = Particle(
        t = random.nextFloat(),
        offset = random.nextFloat() * 3f,
        speed = 18f + random.nextFloat() * 34f,
        size = 0.8f + random.nextFloat() * 2.2f,
        alpha = 120 + random.nextInt(135),
        colorIndex = random.nextInt(colors.size)
    )

    private data class Particle(
        val t: Float,
        val offset: Float,
        val speed: Float,
        val size: Float,
        val alpha: Int,
        val colorIndex: Int
    )
}
