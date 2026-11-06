package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.min

/** 营养环图例，同时显示达标百分比。 */
class NutritionLegendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        color = tokens.textSecondary
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        textAlign = Paint.Align.RIGHT
        color = tokens.textPrimary
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val colors = intArrayOf(
        Color.parseColor("#FF9F43"),
        Color.parseColor("#5AAEFF"),
        Color.parseColor("#65D6A5"),
        Color.parseColor("#FFD166"),
        Color.parseColor("#B18CFF"),
        Color.parseColor("#5AD8D8")
    )
    private val labels = arrayOf("热量", "蛋白质", "碳水", "脂肪", "纤维", "水")
    private var ratios = FloatArray(6)

    fun setData(calories: Double, protein: Double, carbs: Double, fat: Double, fiber: Double, water: Double, goal: com.dsha.sagittarius.data.NutritionGoalEntity) {
        ratios = floatArrayOf(
            ratio(calories, goal.calories),
            ratio(protein, goal.protein),
            ratio(carbs, goal.carbs),
            ratio(fat, goal.fat),
            ratio(fiber, goal.fiber),
            ratio(water, goal.water)
        )
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
            val left = col * cellW + dp(16f)
            val cy = row * cellH + cellH / 2f
            dotPaint.color = colors[i]
            canvas.drawCircle(left, cy, dp(3.2f), dotPaint)
            textPaint.color = tokens.textSecondary
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels[i], left + dp(9f), cy + dp(3.5f), textPaint)
            percentPaint.color = tokens.textPrimary
            canvas.drawText("${(ratios[i] * 100).toInt()}%", col * cellW + cellW - dp(14f), cy + dp(3.5f), percentPaint)
        }
    }

    private fun ratio(value: Double, target: Double): Float =
        if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1.1f)

    private fun dp(value: Float): Float = value * density
}
