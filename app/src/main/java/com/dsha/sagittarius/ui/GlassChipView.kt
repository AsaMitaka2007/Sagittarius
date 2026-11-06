package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import com.dsha.sagittarius.R

/**
 * 胶囊筛选/分类控件。选中态为低透明度三段渐变填充 + 渐变描边；
 * 选中切换时颜色渐变、轻微缩放，按压使用统一回弹。
 */
class GlassChipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val rect = RectF()
    private val accent = ButtonAccentManager.current
    private val colors = accent.colors
    private var selectionProgress = 0f
    private var selectionAnimator: ValueAnimator? = null
    private var scaleXSpring: SpringAnimation? = null
    private var scaleYSpring: SpringAnimation? = null

    init {
        gravity = android.view.Gravity.CENTER
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(15f), dp(8f), dp(15f), dp(8f))
        minHeight = dp(36f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        selectionProgress = if (isSelected) 1f else 0f
        updateTextColor()
    }

    override fun setSelected(selected: Boolean) {
        val changed = isSelected != selected
        super.setSelected(selected)
        if (!changed) {
            updateTextColor()
            invalidate()
            return
        }
        val target = if (selected) 1f else 0f
        if (!isAttachedToWindow || MotionUtils.reduceMotion(context)) {
            selectionAnimator?.cancel()
            selectionProgress = target
            updateTextColor()
            invalidate()
            return
        }
        selectionAnimator?.cancel()
        selectionAnimator = ValueAnimator.ofFloat(selectionProgress, target).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                selectionProgress = it.animatedValue as Float
                updateTextColor()
                invalidate()
            }
        }
        selectionAnimator?.start()

        // 选中态轻弹一下，幅度很小，只做一次。
        MotionUtils.cancelPress(this)
        scaleXSpring?.cancel()
        scaleYSpring?.cancel()
        scaleXSpring = SpringAnimation(this, DynamicAnimation.SCALE_X, 1f).apply {
            spring.dampingRatio = 0.58f
            spring.stiffness = 420f
            setStartValue(0.94f)
            start()
        }
        scaleYSpring = SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = 0.58f
            spring.stiffness = 420f
            setStartValue(0.94f)
            start()
        }
    }

    private fun updateTextColor() {
        setTextColor(
            ColorUtils.blendARGB(
                tokens.chipUnselectedText,
                accent.textColor,
                selectionProgress.coerceIn(0f, 1f)
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val radius = h / 2f
        rect.set(1f, 1f, w - 1f, h - 1f)
        val p = selectionProgress.coerceIn(0f, 1f)

        // 未选中底始终保留，选中渐变叠在上面，随进度淡入。
        if (p < 1f) {
            fillPaint.shader = null
            fillPaint.color = ColorUtils.setAlphaComponent(
                tokens.chipUnselectedFill,
                ((1f - p) * Color.alpha(tokens.chipUnselectedFill)).toInt()
            )
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            borderPaint.strokeWidth = 1f
            borderPaint.shader = null
            borderPaint.alpha = ((1f - p) * Color.alpha(tokens.chipUnselectedStroke)).toInt()
            borderPaint.color = tokens.chipUnselectedStroke
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }

        if (p > 0f) {
            fillPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    ColorUtils.setAlphaComponent(colors[0], (52 * p).toInt()),
                    ColorUtils.setAlphaComponent(colors[1], (40 * p).toInt()),
                    ColorUtils.setAlphaComponent(colors[2], (34 * p).toInt())
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            borderPaint.strokeWidth = 1.2f * density
            borderPaint.alpha = (255 * p).toInt()
            borderPaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(colors[0], colors[1], colors[2]),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
        borderPaint.alpha = 255
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> MotionUtils.press(this)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> MotionUtils.release(this)
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        selectionAnimator?.cancel()
        selectionAnimator = null
        scaleXSpring?.cancel()
        scaleYSpring?.cancel()
        MotionUtils.cancelPress(this)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Int = (value * density).toInt()
}
