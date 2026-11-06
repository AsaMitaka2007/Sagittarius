package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BlurMaskFilter
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.ButtonAccentManager
import com.dsha.sagittarius.ui.MotionUtils
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.sin

/** 推理等级：可拖动的炫彩流星进度条。 */
class ReasoningFlowSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val accent = ButtonAccentManager.current
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(tokens.textTertiary, 42)
        style = Paint.Style.FILL
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val thumbBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private var progressGradient: LinearGradient? = null
    private var barRect = RectF()
    private var thumbRect = RectF()
    private var progress = 0f
    private var dragging = false
    private var lastHapticStep = -1
    private var flow = 0f
    private var animator: ValueAnimator? = null
    private val lowRam = MotionUtils.isLowRam(context)
    private var palette = intArrayOf(
        Color.parseColor("#FF3B30"),
        Color.parseColor("#FF8A00"),
        Color.parseColor("#FFD60A"),
        Color.parseColor("#34C759"),
        Color.parseColor("#00E5FF"),
        Color.parseColor("#4DA8FF"),
        Color.parseColor("#A86BFF")
    )

    /** 拖动中仅更新视觉；松手后才会调用。 */
    var onProgressPreview: ((Int) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null
    var onScrubStateChanged: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "推理等级，可左右拖动"
        minimumHeight = (68 * density).toInt()
    }

    fun setProgress(value: Int) {
        progress = (value.coerceIn(0, 100) / 100f)
        updatePalette()
        invalidate()
    }

    fun setAccent(start: Int, end: Int) {
        // 七色流星轨道固定保留红橙黄绿青蓝紫，按钮颜色只影响滑块/粒子点缀。
        rebuildGradient()
        invalidate()
    }

    private fun updatePalette() {
        rebuildGradient()
    }

    private fun rebuildGradient() {
        if (width <= 0) return
        progressGradient = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            palette, null, Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (MotionUtils.ambientAllowed(context)) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    flow = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradient()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(18f)
        val right = width - dp(18f)
        val cy = height / 2f
        val barHeight = dp(26f)
        val thumbWidth = dp(12f)
        val thumbHeight = dp(28f)
        barRect.set(left, cy - barHeight / 2f, right, cy + barHeight / 2f)
        canvas.drawRoundRect(barRect, dp(9f), dp(9f), trackPaint)
        // 刻度只画在灰轨道上；彩色进度后绘制会覆盖已越过的刻度。
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(tokens.textPrimary, 90)
            strokeWidth = dp(1.5f)
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0..3) {
            val x = left + (right - left) * (i / 3f)
            canvas.drawLine(x, cy - dp(7f), x, cy + dp(7f), tickPaint)
        }

        if (progress > 0.001f) {
            val active = RectF(left, barRect.top, left + (right - left) * progress, barRect.bottom)
            progressGradient?.let { shader ->
                matrix.setTranslate(flow * width * 0.55f, 0f)
                shader.setLocalMatrix(matrix)
            }
            progressPaint.shader = progressGradient
            glowPaint.shader = progressGradient
            glowPaint.alpha = if (lowRam) 0 else 110
            if (!lowRam) canvas.drawRoundRect(active, dp(9f), dp(9f), glowPaint)
            canvas.drawRoundRect(active, dp(9f), dp(9f), progressPaint)
            progressPaint.shader = null
            glowPaint.shader = null
        }

        val thumbX = left + (right - left) * progress
        thumbRect.set(
            thumbX - thumbWidth / 2f,
            cy - thumbHeight / 2f,
            thumbX + thumbWidth / 2f,
            cy + thumbHeight / 2f
        )
        // 白色矩形滑块，无光晕；边缘只保留一圈极细的强调色。
        canvas.drawRoundRect(thumbRect, dp(3f), dp(3f), thumbPaint)
        thumbBorder.color = ColorUtils.setAlphaComponent(accent.endColor, 220)
        canvas.drawRoundRect(thumbRect, dp(3f), dp(3f), thumbBorder)

        if (dragging && !lowRam) {
            for (i in 0 until 5) {
                val angle = flow * Math.PI * 2.0 + i * Math.PI * 0.4
                val radius = thumbWidth * (1.7f + 0.45f * sin(flow * 8f + i))
                val px = thumbX + (Math.cos(angle) * radius).toFloat()
                val py = cy + (Math.sin(angle) * radius).toFloat()
                particlePaint.color = ColorUtils.setAlphaComponent(palette[i % palette.size], 160)
                canvas.drawRoundRect(px - dp(1.5f), py - dp(3f), px + dp(1.5f), py + dp(3f), dp(2f), dp(2f), particlePaint)
            }
        }

        if (progress >= 0.75f && !lowRam) {
            // 深度思考：粒子从右向左加速，接近左端淡出。
            for (i in 0 until 9) {
                val t = ((flow * 1.6f + i / 9f) % 1f)
                val eased = t * t
                val x = right - (right - left) * eased
                val alpha = ((1f - eased) * 210f).toInt().coerceIn(0, 210)
                val half = dp(1.2f + 1.2f * (1f - eased))
                particlePaint.color = ColorUtils.setAlphaComponent(palette[i % palette.size], alpha)
                canvas.drawRoundRect(
                    x - half, cy - dp(2f), x + half, cy + dp(2f),
                    dp(2f), dp(2f), particlePaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                onScrubStateChanged?.invoke(true)
                updateFromX(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) updateFromX(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                onScrubStateChanged?.invoke(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onProgressChanged?.invoke((progress * 100).toInt())
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromX(x: Float) {
        val left = dp(18f)
        val right = width - dp(18f)
        val next = ((x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
        progress = next
        updatePalette()
        onProgressPreview?.invoke((progress * 100).toInt())
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.55f
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * density
}
