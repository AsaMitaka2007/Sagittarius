package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
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
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.sin

/**
 * 主要操作按钮：渐变填充 + 缓慢流动的描边 + 弹性按压。
 *
 * 描边使用 SweepGradient 围绕按钮边缘旋转，渐变填充只做极小幅度的呼吸位移。
 * 低内存或系统“移除动画”时只保留静态渐变与按压回弹。
 */
class GradientActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    // TextView 构造期间会触发 setEnabled() -> drawableStateChanged()，
    // 此时 Kotlin 属性尚未完成初始化，必须等 init 里 tokens 可用后再着色。
    private var tokensReady = false
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.35f * density
    }
    private val underBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        color = tokens.cardShadow
    }
    private val rect = RectF()
    private val glowRect = RectF()
    private val clipPath = Path()
    private val fillMatrix = Matrix()
    private val borderMatrix = Matrix()
    private var fillShader: LinearGradient? = null
    private var borderShader: SweepGradient? = null
    private var highlightShader: LinearGradient? = null
    private var phase = 0f
    private var phaseAnimator: ValueAnimator? = null
    private var ambientAllowed = false
    private var cachedWidth = 0
    private var cachedHeight = 0

    private val accent = ButtonAccentManager.current
    private val fillColors = accent.colors
    private val borderColors = intArrayOf(
        accent.startColor,
        accent.endColor,
        accent.startColor,
        accent.startColor
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        stateListAnimator = null
        isAllCaps = false
        gravity = android.view.Gravity.CENTER
        minHeight = dp(58f)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setPadding(dp(20f), dp(10f), dp(20f), dp(10f))
        ambientAllowed = MotionUtils.ambientAllowed(context)
        tokensReady = true
        updateTextColor()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w
        cachedHeight = h
        buildShaders()
    }

    private fun buildShaders() {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        fillShader = LinearGradient(
            -w * 0.22f, 0f, w * 1.22f, 0f,
            fillColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        borderShader = SweepGradient(
            w / 2f, h / 2f,
            borderColors,
            floatArrayOf(0f, 0.32f, 0.66f, 1f)
        )
        val top = dp(4f).toFloat()
        highlightShader = LinearGradient(
            top, top, top, top + (h - 2 * top) * 0.55f,
            intArrayOf(ColorUtils.setAlphaComponent(tokens.cardHighlight, 70), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val inset = dp(4f).toFloat()
        rect.set(inset, inset, width - inset, height - inset)
        val radius = 18f * density
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        if (isEnabled) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 1.2f * density
            for (i in 4 downTo 1) {
                glowPaint.color = ColorUtils.setAlphaComponent(
                    if (i % 2 == 0) accent.startColor else accent.endColor,
                    4 + (4 - i) * 3
                )
                glowRect.set(
                    rect.left - i * density,
                    rect.top - i * density,
                    rect.right + i * density,
                    rect.bottom + i * density
                )
                canvas.drawRoundRect(glowRect, radius + i * density, radius + i * density, glowPaint)
            }

            fillShader?.let { shader ->
                val breathing = sin(phase * 2f * PI).toFloat() * width * 0.035f
                fillMatrix.setTranslate(breathing, 0f)
                shader.setLocalMatrix(fillMatrix)
                fillPaint.shader = shader
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }

            highlightPaint.shader = highlightShader
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRect(rect, highlightPaint)
            canvas.restore()

            canvas.drawRoundRect(rect, radius, radius, underBorderPaint)
            borderShader?.let { shader ->
                borderMatrix.setRotate(phase * 360f, width / 2f, height / 2f)
                shader.setLocalMatrix(borderMatrix)
                borderPaint.shader = shader
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }
        } else {
            fillPaint.shader = null
            fillPaint.color = ColorUtils.setAlphaComponent(tokens.textPrimary, 34)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            borderPaint.shader = null
            borderPaint.color = ColorUtils.setAlphaComponent(tokens.textPrimary, 50)
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> MotionUtils.press(this)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> MotionUtils.release(this)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateTextColor()
        updateAmbientAnimator()
        invalidate()
    }

    private fun updateTextColor() {
        if (!tokensReady) return
        setTextColor(
            ColorStateList.valueOf(if (isEnabled) accent.textColor else tokens.buttonDisabledText)
        )
    }

    private fun ensureAnimator() {
        if (phaseAnimator != null) return
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_600L
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
        val shouldRun = ambientAllowed && isEnabled && isAttachedToWindow &&
            isShown && windowVisibility == View.VISIBLE
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
        if (cachedWidth != width || cachedHeight != height) buildShaders()
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Int = (value * density).toInt()
}
