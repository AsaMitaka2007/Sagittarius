package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Locale

/** 维生素细分展示：2 列 × 3 行，仅作参考，不参与达标计算。 */
class VitaminGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        color = tokens.textSecondary
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        textAlign = Paint.Align.RIGHT
        color = tokens.textPrimary
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val labels = arrayOf("维生素 A", "维生素 B1", "维生素 B2", "烟酸 B3", "维生素 C", "维生素 E")
    private var values = DoubleArray(6)

    /** 顺序：A(μg)、B1、B2、B3、C、E(mg)。 */
    fun setData(vitaminA: Double, vitaminB1: Double, vitaminB2: Double, vitaminB3: Double, vitaminC: Double, vitaminE: Double) {
        values = doubleArrayOf(vitaminA, vitaminB1, vitaminB2, vitaminB3, vitaminC, vitaminE)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val columns = 2
        val rows = 3
        val cellW = width / columns.toFloat()
        val cellH = height / rows.toFloat()
        for (i in 0 until 6) {
            val col = i % columns
            val row = i / columns
            val left = col * cellW + dp(4f)
            val cy = row * cellH + cellH / 2f
            labelPaint.color = tokens.textSecondary
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels[i], left, cy + dp(3.5f), labelPaint)
            valuePaint.color = tokens.textPrimary
            canvas.drawText(format(i, values[i]), col * cellW + cellW - dp(8f), cy + dp(3.5f), valuePaint)
        }
    }

    private fun format(index: Int, value: Double): String = when (index) {
        0 -> String.format(Locale.CHINA, "%.0f μg", value)
        1, 2, 3 -> String.format(Locale.CHINA, "%.2f mg", value)
        else -> String.format(Locale.CHINA, "%.1f mg", value)
    }

    private fun dp(value: Float): Float = value * density
}
