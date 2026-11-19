package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.data.NutritionGoalEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * 复合营养环：最外两圈为钠与维生素，向内依次为热量、碳水、脂肪、蛋白质、纤维、水。
 * 触摸某圈时通过 [onRingSelected] 回调圈序号，由页面展示该项明细。
 */
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
    private val rect = RectF()

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var calories = 0.0
    private var carbs = 0.0
    private var fat = 0.0
    private var protein = 0.0
    private var fiber = 0.0
    private var water = 0.0
    private var sodium = 0.0
    private var vitamin = 0.0
    private var goal = NutritionGoalEntity()
    private var lastTouchedIndex = -1

    /** 触摸到某一圈时回调 0..7；0 为钠，1 为维生素，其余为原六项。 */
    var onRingSelected: ((Int) -> Unit)? = null

    private val ringColors = intArrayOf(
        Color.parseColor("#FF7B9C"),
        Color.parseColor("#A3E635"),
        Color.parseColor("#FF9F43"),
        Color.parseColor("#65D6A5"),
        Color.parseColor("#FFD166"),
        Color.parseColor("#5AAEFF"),
        Color.parseColor("#B18CFF"),
        Color.parseColor("#5AD8D8")
    )

    init {
        isClickable = true
        contentDescription = "营养环，触摸查看各项摄入明细"
    }

    fun setData(
        calories: Double, carbs: Double, fat: Double, protein: Double,
        fiber: Double, water: Double, sodium: Double, vitamin: Double,
        goal: NutritionGoalEntity, animate: Boolean = true
    ) {
        this.calories = calories
        this.carbs = carbs
        this.fat = fat
        this.protein = protein
        this.fiber = fiber
        this.water = water
        this.sodium = sodium
        this.vitamin = vitamin
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
        val ringWidth = dp(7.5f)
        val gap = dp(3.5f)
        val values = listOf(sodium, vitamin, calories, carbs, fat, protein, fiber, water)
        val targets = listOf(
            goal.sodium, goal.vitamin, goal.calories, goal.carbs,
            goal.fat, goal.protein, goal.fiber, goal.water
        )
        val ratios = values.indices.map { ratio(values[it], targets[it]) }
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
        }
        // 钠是上限指标，不计入“达标”平均；维生素总量参与达标。
        val score = (ratios.drop(1).average() * 100).toInt().coerceIn(0, 100)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val index = ringIndexAt(event.x, event.y)
                if (index >= 0 && index != lastTouchedIndex) {
                    lastTouchedIndex = index
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onRingSelected?.invoke(index)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                lastTouchedIndex = -1
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                lastTouchedIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun ringIndexAt(x: Float, y: Float): Int {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f - dp(8f)
        val step = dp(11f)
        val distance = hypot(x - cx, y - cy)
        if (distance > maxRadius + dp(8f)) return -1
        for (index in 0 until 8) {
            val radius = maxRadius - index * step
            if (abs(distance - radius) <= dp(7f)) return index
        }
        return -1
    }

    private fun ratio(value: Double, target: Double): Float =
        if (target <= 0.0) 0f else (value / target).toFloat().coerceIn(0f, 1f)

    private fun dp(value: Float): Float = value * density
}
