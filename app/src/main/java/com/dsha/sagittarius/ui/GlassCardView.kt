package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils

/**
 * 液态玻璃卡片：半透明渐变底、顶部高光、内阴影和渐变描边。
 *
 * 主操作行可以开启 setFlowEnabled(true)，描边会缓慢旋转；同屏开启数量由调用方限制为 2。
 * 低内存或系统“移除动画”时自动退回静态描边。
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val isLowRam: Boolean by lazy {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val underBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.1f * density
        color = tokens.cardShadow
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = tokens.cardShadow
    }
    private val rect = RectF()
    private val innerRect = RectF()
    private val clipPath = Path()
    private val flowMatrix = Matrix()
    private var fillShader: Shader? = null
    private var borderShader: Shader? = null
    private var flowShader: SweepGradient? = null
    private var highlightShader: Shader? = null
    private var cachedWidth = 0
    private var cachedHeight = 0

    private var flowEnabled = false
    private var phase = 0f
    private var phaseAnimator: ValueAnimator? = null
    private var ambientAllowed = false

    init {
        setWillNotDraw(false)
        ambientAllowed = MotionUtils.ambientAllowed(context)
    }

    fun setFlowEnabled(enabled: Boolean) {
        if (flowEnabled == enabled) return
        flowEnabled = enabled
        updateAmbientAnimator()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w
        cachedHeight = h
        buildShaders()
    }

    private fun buildShaders() {
        if (width <= 0 || height <= 0) return
        rect.set(1f, 1f, width - 1f, height - 1f)
        val radius = 24f * density
        if (isLowRam) {
            fillShader = null
            borderShader = null
            flowShader = null
            highlightShader = null
            return
        }
        fillShader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(tokens.cardFillTop, tokens.cardFillBottom),
            null,
            Shader.TileMode.CLAMP
        )
        borderShader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                tokens.cardStrokeStart,
                tokens.cardStrokeMid,
                tokens.cardStrokeEnd,
                ColorUtils.setAlphaComponent(tokens.textPrimary, 30)
            ),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        flowShader = SweepGradient(
            width / 2f, height / 2f,
            intArrayOf(tokens.flowStart, tokens.flowMid, tokens.flowEnd, tokens.flowStart),
            floatArrayOf(0f, 0.33f, 0.66f, 1f)
        )
        highlightShader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + height * 0.48f,
            intArrayOf(
                ColorUtils.setAlphaComponent(tokens.cardHighlight, 26),
                ColorUtils.setAlphaComponent(tokens.cardHighlight, 4),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cachedWidth != width || cachedHeight != height) buildShaders()
        if (rect.width() <= 0f) return
        val radius = 24f * density

        if (isLowRam) {
            fillPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillTop, 230)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            borderPaint.color = tokens.cardStrokeStart
            borderPaint.shader = null
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            return
        }

        fillPaint.shader = fillShader
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        innerRect.set(
            rect.left + 3f * density,
            rect.top + 3f * density,
            rect.right - 3f * density,
            rect.bottom - 3f * density
        )
        canvas.drawRoundRect(innerRect, radius, radius, shadowPaint)

        canvas.save()
        canvas.clipPath(clipPath)
        highlightPaint.shader = highlightShader
        canvas.drawRect(rect, highlightPaint)
        canvas.restore()

        if (flowEnabled) {
            canvas.drawRoundRect(rect, radius, radius, underBorderPaint)
            flowShader?.let { shader ->
                flowMatrix.setRotate(phase * 360f, width / 2f, height / 2f)
                shader.setLocalMatrix(flowMatrix)
                borderPaint.strokeWidth = 1.35f * density
                borderPaint.shader = shader
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }
        } else {
            borderPaint.strokeWidth = 1f
            borderPaint.shader = borderShader
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    override fun setPressed(pressed: Boolean) {
        val wasPressed = isPressed
        super.setPressed(pressed)
        if (pressed == wasPressed || !isClickable || !isEnabled) return
        if (pressed) MotionUtils.press(this) else MotionUtils.release(this)
    }

    private fun ensureAnimator() {
        if (phaseAnimator != null) return
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }
    }

    private fun updateAmbientAnimator() {
        ambientAllowed = MotionUtils.ambientAllowed(context)
        val shouldRun = flowEnabled && ambientAllowed && isAttachedToWindow &&
            isShown && windowVisibility == View.VISIBLE && !isLowRam
        if (shouldRun) {
            ensureAnimator()
            phaseAnimator?.let {
                if (it.isPaused) it.resume() else if (!it.isStarted) it.start()
            }
        } else {
            if (phaseAnimator?.isStarted == true) phaseAnimator?.pause()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ambientAllowed = MotionUtils.ambientAllowed(context)
        updateAmbientAnimator()
    }

    override fun onDetachedFromWindow() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        phase = 0f
        MotionUtils.cancelPress(this)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateAmbientAnimator()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateAmbientAnimator()
    }
}
