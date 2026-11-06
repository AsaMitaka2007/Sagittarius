package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.data.NutritionGoalEntity
import java.util.Locale
import kotlin.math.min

/** 复合营养环：最外圈热量，内圈碳水/脂肪/蛋白质/纤维/水。 */
class NutritionRingsView @JvmOverloads constructor(
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
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = tokens.textSecondary
        textSize = 11f * density
    }
    private val centerValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = tokens.textPrimary
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        textSize = 28f * density
    }
    private val centerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = tokens.textTertiary
        textSize = 10f * density
    }
    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 7.5f * density
        color = tokens.textPrimary
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val rect = RectF()
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var calories = 0.0
    private var carbs = 0.0
    private var fat = 0.0
    private var protein = 0.0
    private var fiber = 0.0
    private var water = 0.0
    private var goal = NutritionGoalEntity()

    private val ringColors = intArrayOf(
        Color.parseColor("#FF9F43"),
        Color.parseColor("#65D6A5"),
        Color.parseColor("#FFD166"),
        Color.parseColor("#5AAEFF"),
        Color.parseColor("#B18CFF"),
        Color.parseColor("#5AD8D8")
    )

    fun setData(
        calories: Double, carbs: Double, fat: Double, protein: Double,
        fiber: Double, water: Double, goal: NutritionGoalEntity, animate: Boolean = true
    ) {
        this.calories = calories
        this.carbs = carbs
        this.fat = fat
        this.protein = protein
        this.fiber = fiber
        this.water = water
        this.goal = goal
        animator?.cancel()
        if (!animate) {
            progress = 1f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 780L
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
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f - dp(8f)
        val ringWidth = dp(8f)
        val gap = dp(4f)
        val ratios = listOf(
            ratio(calories, goal.calories),
            ratio(carbs, goal.carbs),
            ratio(fat, goal.fat),
            ratio(protein, goal.protein),
            ratio(fiber, goal.fiber),
            ratio(water, goal.water)
        )
        val start = -90f
        ratios.forEachIndexed { index, ratio ->
            val radius = maxRadius - index * (ringWidth + gap)
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            trackPaint.strokeWidth = ringWidth
            trackPaint.color = ColorUtils.setAlphaComponent(ringColors[index], 32)
            canvas.drawArc(rect, 0f, 360f, false, trackPaint)
            arcPaint.strokeWidth = ringWidth
            arcPaint.color = ringColors[index]
            canvas.drawArc(rect, start, 300f * ratio * progress, false, arcPaint)
            if (progress > 0.2f && ratio > 0.01f) {
                val angle = start + 300f * ratio * progress
                canvas.save()
                canvas.rotate(angle, cx, cy)
                percentPaint.color = ColorUtils.setAlphaComponent(ringColors[index], 235)
                canvas.drawText("${(ratio * 100).toInt()}%", cx, cy - radius + ringWidth + dp(8f), percentPaint)
                canvas.restore()
            }
        }
        val score = (ratios.average() * 100).toInt().coerceIn(0, 100)
        centerTitlePaint.color = tokens.textSecondary
        centerTitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("今日总热量", cx, cy - dp(28f), centerTitlePaint)
        centerValuePaint.color = tokens.textPrimary
        centerValuePaint.textAlign = Paint.Align.CENTER
        centerValuePaint.textSize = dp(28f)
        canvas.drawText(String.format(Locale.CHINA, "%.0f", calories), cx, cy + dp(8f), centerValuePaint)
        centerSubPaint.color = tokens.textTertiary
        centerSubPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("kcal · 达标 $score%", cx, cy + dp(28f), centerSubPaint)
    }

    private fun ratio(value: Double, target: Double): Float =
        if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1f)

    private fun dp(value: Float): Float = value * density
}
