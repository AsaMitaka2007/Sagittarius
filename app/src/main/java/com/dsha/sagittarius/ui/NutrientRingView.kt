package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 小型营养进度环。
 *
 * 与营养页主环同一视觉语言：圆环 + 300° 进度 + 中心数值，
 * 下方显示“名称 + 百分比”。触摸或鼠标移入时通过 [onSelected]
 * 把具体摄入量回调给页面，用于展示明细。
 */
class NutrientRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = tokens.textPrimary
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = tokens.textTertiary
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = tokens.textSecondary
        textSize = 10f * density
    }
    private val rect = RectF()

    private var shortLabel = ""
    private var fullLabel = ""
    private var value = 0.0
    private var target = 0.0
    private var unit = ""
    private var decimals = 0
    private var ringColor = tokens.accent
    private var referenceLabel = "参考摄入量"
    private var warnWhenOver = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var lastDetail: String? = null

    /** 触摸圆环时回调完整描述，例如“维生素 C：32.0 mg / 参考摄入量 100 mg（32%）”。 */
    var onSelected: ((String) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    fun setData(
        shortLabel: String,
        fullLabel: String,
        value: Double,
        target: Double,
        unit: String,
        color: Int,
        decimals: Int = 0,
        referenceLabel: String = "参考摄入量",
        warnWhenOver: Boolean = false,
        animate: Boolean = true
    ) {
        this.shortLabel = shortLabel
        this.fullLabel = fullLabel
        this.value = value
        this.target = target
        this.unit = unit
        this.decimals = decimals
        this.ringColor = color
        this.referenceLabel = referenceLabel
        this.warnWhenOver = warnWhenOver
        contentDescription = detail()
        animator?.cancel()
        if (!animate) {
            progress = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val labelSpace = dp(30f)
        val ringArea = (height - labelSpace).coerceAtLeast(dp(40f))
        val cx = width / 2f
        val cy = ringArea / 2f
        val radius = min(width.toFloat(), ringArea) / 2f - dp(8f)
        if (radius <= 0f) return

        val ratio = ratio()
        val over = warnWhenOver && target > 0.0 && value > target
        trackPaint.strokeWidth = dp(7f)
        trackPaint.color = ColorUtils.setAlphaComponent(ringColor, 38)
        canvas.drawCircle(cx, cy, radius, trackPaint)

        arcPaint.strokeWidth = dp(7f)
        arcPaint.color = if (over) tokens.warning else ringColor
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, -90f, 300f * ratio.coerceIn(0f, 1f) * progress, false, arcPaint)

        val valueSize = (min(width.toFloat(), ringArea) * 0.15f).coerceIn(dp(12f), dp(22f))
        valuePaint.textSize = valueSize
        valuePaint.color = tokens.textPrimary
        canvas.drawText(formatValue(value), cx, cy + valueSize * 0.34f, valuePaint)
        unitPaint.textSize = valueSize * 0.46f
        unitPaint.color = tokens.textTertiary
        canvas.drawText(unit, cx, cy + valueSize * 0.34f + valueSize * 0.62f, unitPaint)

        labelPaint.color = if (over) tokens.warning else tokens.textSecondary
        canvas.drawText("$shortLabel  ${percent()}%", cx, height - dp(8f), labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                select()
                return true
            }
            MotionEvent.ACTION_UP -> {
                select()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        ) {
            select()
        }
        return super.onHoverEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        select()
        return true
    }

    private fun select() {
        val text = detail()
        if (text == lastDetail) return
        lastDetail = text
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onSelected?.invoke(text)
    }

    private fun detail(): String {
        val overText = if (warnWhenOver && target > 0.0 && value > target) "，已超出参考上限" else ""
        return "$fullLabel：${formatValue(value)} $unit / $referenceLabel ${formatTarget()} $unit（${percent()}%）$overText"
    }

    private fun ratio(): Float =
        if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1f)

    private fun percent(): Int =
        if (target <= 0.0) 0 else (value / target * 100.0).roundToInt()

    private fun formatValue(value: Double): String =
        String.format(Locale.CHINA, "%.${decimals}f", value)

    private fun formatTarget(): String =
        String.format(Locale.CHINA, "%.${decimals}f", target)

    private fun dp(value: Float): Float = value * density
}
