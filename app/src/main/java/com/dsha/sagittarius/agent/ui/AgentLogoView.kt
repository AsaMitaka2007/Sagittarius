package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.MotionUtils
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 新会话空态的 Sagittarius 星座 logo。
 *
 * 每个节点用与进度条同款的四角星芒绘制，节点间用会呼吸的发光细线连接；
 * 星星缓慢漂移、轻微自转，连线跟随节点实时重绘。
 */
class AgentLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val sparklePath = Path()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val points = FloatArray(nodes.size * 2)
    private val driftSpeedX = FloatArray(nodeCount)
    private val driftSpeedY = FloatArray(nodeCount)
    private val driftAmp = FloatArray(nodeCount)
    private val driftPhase = FloatArray(nodeCount)
    private val spinSpeed = FloatArray(nodeCount)
    private val starSize = FloatArray(nodeCount)
    private val edgePhase = FloatArray(edges.size / 2)
    private var time = 0f
    private var startedAt = 0L
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        contentDescription = "Sagittarius 星座"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // 四角星芒轮廓，与进度条完全一致
        sparklePath.moveTo(0f, -1f)
        sparklePath.quadTo(0.18f, -0.18f, 1f, 0f)
        sparklePath.quadTo(0.18f, 0.18f, 0f, 1f)
        sparklePath.quadTo(-0.18f, 0.18f, -1f, 0f)
        sparklePath.quadTo(-0.18f, -0.18f, 0f, -1f)
        sparklePath.close()
        val random = Random(19)
        for (i in 0 until nodeCount) {
            driftSpeedX[i] = 0.24f + random.nextFloat() * 0.28f
            driftSpeedY[i] = 0.20f + random.nextFloat() * 0.26f
            driftAmp[i] = (1.1f + random.nextFloat() * 1.2f)
            driftPhase[i] = random.nextFloat() * 6.28f
            spinSpeed[i] = 0.10f + random.nextFloat() * 0.22f
            starSize[i] = 3.1f + random.nextFloat() * 2.0f
        }
        for (i in edgePhase.indices) edgePhase[i] = random.nextFloat() * 6.28f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat() * 0.92f
        if (size <= 0f) return
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        for (i in 0 until nodeCount) {
            val phase = driftPhase[i]
            val dx = sin(time * driftSpeedX[i] + phase) * driftAmp[i] * density
            val dy = cos(time * driftSpeedY[i] + phase * 1.3f) * driftAmp[i] * density
            points[i * 2] = left + nodes[i * 2] * size + dx
            points[i * 2 + 1] = top + nodes[i * 2 + 1] * size + dy
        }

        var edge = 0
        while (edge < edges.size) {
            val a = edges[edge]
            val b = edges[edge + 1]
            val breath = sin(time * 0.62f + edgePhase[edge / 2]) * 0.5f + 0.5f
            val alpha = (0.42f + 0.58f * breath).coerceIn(0f, 1f)
            val x1 = points[a * 2]
            val y1 = points[a * 2 + 1]
            val x2 = points[b * 2]
            val y2 = points[b * 2 + 1]
            // 外层柔光
            linePaint.strokeWidth = 3.4f * density
            linePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (alpha * 26f).toInt())
            canvas.drawLine(x1, y1, x2, y2, linePaint)
            // 细亮线
            linePaint.strokeWidth = 0.9f * density
            linePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (alpha * 205f).toInt())
            canvas.drawLine(x1, y1, x2, y2, linePaint)
            edge += 2
        }

        for (i in 0 until nodeCount) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]
            val radius = starSize[i] * density
            val rotation = (time * spinSpeed[i] + driftPhase[i]) * 57.2958f
            val twinkle = sin(time * 0.5f + driftPhase[i] * 1.7f) * 0.5f + 0.5f
            val coreAlpha = (150f + 105f * twinkle).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)
            canvas.scale(radius * 2.5f, radius * 2.5f)
            sparklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (coreAlpha * 0.14f).toInt())
            canvas.drawPath(sparklePath, sparklePaint)
            canvas.restore()

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)
            canvas.scale(radius, radius)
            sparklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, coreAlpha)
            canvas.drawPath(sparklePath, sparklePaint)
            canvas.restore()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimator()
    }

    override fun onDetachedFromWindow() {
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) startAnimator() else stopAnimator()
    }

    private fun startAnimator() {
        if (!MotionUtils.ambientAllowed(context)) {
            time = 0f
            invalidate()
            return
        }
        if (animator?.isStarted == true) return
        startedAt = SystemClock.uptimeMillis()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                time = (SystemClock.uptimeMillis() - startedAt) / 1000f
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimator() {
        animator?.cancel()
        animator = null
    }

    companion object {
        // 从 sag.png 提取的 21 个星点（归一化坐标）
        private val nodes = floatArrayOf(
            0.664f, 0.045f,
            0.200f, 0.167f,
            0.238f, 0.182f,
            0.387f, 0.184f,
            0.357f, 0.222f,
            0.884f, 0.192f,
            0.614f, 0.227f,
            0.793f, 0.312f,
            0.526f, 0.312f,
            0.452f, 0.306f,
            0.695f, 0.331f,
            0.405f, 0.402f,
            0.200f, 0.420f,
            0.452f, 0.451f,
            0.723f, 0.476f,
            0.777f, 0.533f,
            0.087f, 0.600f,
            0.217f, 0.795f,
            0.466f, 0.830f,
            0.525f, 0.942f,
            0.338f, 0.964f
        )
        private val nodeCount = nodes.size / 2

        // 与原图一致的连线（索引为 nodes 的序号）
        private val edges = intArrayOf(
            0, 6,
            1, 2,
            2, 4,
            3, 4,
            3, 9,
            4, 9,
            5, 7,
            6, 8,
            6, 10,
            7, 10,
            7, 14,
            8, 9,
            8, 10,
            8, 13,
            9, 11,
            10, 14,
            11, 12,
            11, 13,
            12, 16,
            13, 14,
            14, 15,
            16, 17,
            17, 20,
            18, 20,
            19, 20
        )
    }
}
