package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.min

/** 饮食页中心的透明营养气泡。 */
class DietBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }
    private val totalPaint = textPaint(38f, tokens.textPrimary, true)
    private val unitPaint = textPaint(10f, tokens.textTertiary)
    private val hintPaint = textPaint(10f, tokens.textSecondary)
    private val rect = RectF()
    private var calories = 0.0
    private var grams = 0.0
    private var error = false
    private var lines: List<String> = emptyList()

    init {
        setWillNotDraw(false)
    }

    fun setSummary(calories: Double, grams: Double, lines: List<String>) {
        this.calories = calories
        this.grams = grams
        this.lines = lines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height).toFloat() - dp(8f)
        val cx = width / 2f
        val cy = height / 2f
        val radius = diameter / 2f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        fillPaint.shader = RadialGradient(
            cx - radius * 0.32f,
            cy - radius * 0.38f,
            radius * 1.35f,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, 70),
                tokens.cardFillTop,
                tokens.cardFillBottom
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null
        borderPaint.color = if (error) Color.parseColor("#E35D5D") else tokens.cardStrokeStart
        borderPaint.strokeWidth = if (error) 2.5f * density else 1.3f * density
        canvas.drawCircle(cx, cy, radius, borderPaint)
        borderPaint.strokeWidth = 1.3f * density

        val kcalText = String.format(Locale.CHINA, "%.0f", calories)
        totalPaint.color = tokens.textPrimary
        totalPaint.textAlign = Paint.Align.CENTER
        totalPaint.textSize = fitSize(totalPaint, kcalText, radius * 1.5f)
        canvas.drawText(kcalText, cx, cy + dp(6f), totalPaint)
        unitPaint.color = tokens.textTertiary
        unitPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("kcal · ${String.format(Locale.CHINA, "%.0f", grams)}g", cx, cy + dp(22f), unitPaint)
        if (lines.isEmpty()) {
            hintPaint.color = tokens.textSecondary
            hintPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("点击食物开始记录", cx, cy + dp(38f), hintPaint)
        } else {
            hintPaint.color = tokens.textSecondary
            hintPaint.textAlign = Paint.Align.CENTER
            val label = lines.takeLast(2).joinToString(" · ")
            canvas.drawText(fit(hintPaint, label, radius * 1.55f), cx, cy + dp(38f), hintPaint)
        }
    }

    fun flashError() {
        error = true
        invalidate()
        animate().cancel()
        animate().scaleX(1.06f).scaleY(1.06f).setDuration(90L)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
            }
            .start()
        postDelayed({ error = false; invalidate() }, 520L)
    }

    private fun fitSize(paint: Paint, text: String, maxWidth: Float): Float {
        var size = 38f * density
        while (size > dp(16f) && paint.measureText(text) > maxWidth) {
            size -= dp(1f)
            paint.textSize = size
        }
        return size
    }

    private fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun textPaint(sp: Float, color: Int, medium: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp * density
        this.color = color
        typeface = android.graphics.Typeface.create(
            if (medium) "sans-serif-medium" else "sans-serif",
            android.graphics.Typeface.NORMAL
        )
    }

    private fun dp(value: Float): Float = value * density
}
