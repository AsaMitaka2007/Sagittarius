package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.R
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.util.Money
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 消费比伪 3D 饼图：椭圆 + 向下偏移的侧面厚度。
 * 低端机关闭厚度与动画，退化为平面 2D。
 */
class StatsPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val lowEnd = MotionUtils.isLowRam(context)
    private val reduceMotion = MotionUtils.reduceMotion(context)

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = ColorUtils.setAlphaComponent(tokens.divider, 150)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = tokens.cardStrokeStart
    }
    private val titlePaint = textPaint(10f, tokens.textSecondary)
    private val amountPaint = textPaint(12f, tokens.textPrimary, medium = true, tabular = true)
    private val legendPaint = textPaint(10f, tokens.textSecondary)
    private val emptyPaint = textPaint(11f, tokens.textTertiary)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val tooltipRect = RectF()

    private var slices: List<PieSlice> = emptyList()
    private var selectedIndex = -1
    private var reveal = 1f
    private var animator: ValueAnimator? = null
    private var centerX = 0f
    private var centerY = 0f
    private var radiusX = 0f
    private var radiusY = 0f

    init {
        isClickable = true
        contentDescription = "消费比饼图"
    }

    fun setData(data: List<PieSlice>) {
        slices = data
        selectedIndex = -1
        animator?.cancel()
        if (!isAttachedToWindow || !isShown || lowEnd || reduceMotion) {
            reveal = 1f
            invalidate()
            return
        }
        reveal = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((320f * density).toInt(), widthMeasureSpec)
        val height = resolveSize((230f * density).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScrim(canvas)
        if (slices.isEmpty()) {
            emptyPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.stats_empty), width / 2f, height / 2f, emptyPaint)
            return
        }

        centerX = width / 2f
        centerY = height / 2f - dp(2f)
        radiusX = min(width * 0.36f, dp(112f))
        radiusY = radiusX * 0.72f
        val depth = if (lowEnd) 0f else dp(16f)
        val sweepTotal = 360f * reveal

        if (depth > 0f) {
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.share * sweepTotal
                if (sweep > 0f) {
                    sidePaint.color = ColorUtils.blendARGB(slice.color, Color.BLACK, 0.28f)
                    oval.set(
                        centerX - radiusX,
                        centerY - radiusY + depth,
                        centerX + radiusX,
                        centerY + radiusY + depth
                    )
                    canvas.drawArc(oval, start, sweep, true, sidePaint)
                }
                start += sweep
            }
        }

        var start = -90f
        slices.forEachIndexed { index, slice ->
            val sweep = slice.share * sweepTotal
            if (sweep > 0f) {
                val selected = index == selectedIndex
                val mid = Math.toRadians((start + sweep / 2f).toDouble())
                val offset = if (selected) dp(5f) else 0f
                val ox = (cos(mid) * offset).toFloat()
                val oy = (sin(mid) * offset * 0.72f).toFloat()
                oval.set(
                    centerX - radiusX + ox,
                    centerY - radiusY + oy,
                    centerX + radiusX + ox,
                    centerY + radiusY + oy
                )
                slicePaint.color = if (selected) {
                    slice.color
                } else {
                    ColorUtils.setAlphaComponent(slice.color, 232)
                }
                canvas.drawArc(oval, start, sweep, true, slicePaint)
                canvas.drawArc(oval, start, sweep, false, strokePaint)
                if (selected) {
                    slicePaint.style = Paint.Style.STROKE
                    slicePaint.strokeWidth = 2.5f * density
                    slicePaint.color = tokens.textPrimary
                    canvas.drawArc(oval, start, sweep, false, slicePaint)
                    slicePaint.style = Paint.Style.FILL
                }
            }
            start += sweep
        }

        if (selectedIndex in slices.indices) {
            drawTooltip(canvas, slices[selectedIndex])
        }
    }

    private fun drawScrim(canvas: Canvas) {
        if (!ThemeManager.isGlass) return
        scrimPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillBottom, 24)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    private fun drawTooltip(canvas: Canvas, slice: PieSlice) {
        val title = slice.label
        val amount = Money.formatCents(slice.amountCents)
        val share = "占比 ${Money.sharePercent(slice.share)}%"
        val contentWidth = maxOf(
            titlePaint.measureText(title),
            amountPaint.measureText(amount),
            legendPaint.measureText(share)
        )
        val cardWidth = min(contentWidth + dp(20f), width - dp(16f))
        val cardHeight = dp(62f)
        val left = dp(8f)
        val top = dp(6f)
        tooltipRect.set(left, top, left + cardWidth, top + cardHeight)
        cardPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillTop, 245)
        canvas.drawRoundRect(tooltipRect, dp(10f), dp(10f), cardPaint)
        canvas.drawRoundRect(tooltipRect, dp(10f), dp(10f), cardStrokePaint)
        canvas.drawText(title, left + dp(9f), top + dp(16f), titlePaint)
        canvas.drawText(amount, left + dp(9f), top + dp(34f), amountPaint)
        canvas.drawText(share, left + dp(9f), top + dp(51f), legendPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (slices.isEmpty() || radiusX <= 0f) return true
                val dx = (event.x - centerX) / radiusX
                val dy = (event.y - centerY) / radiusY
                if (dx * dx + dy * dy <= 1.08f) {
                    val angle = Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0
                    val normalized = ((angle % 360.0) + 360.0) % 360.0
                    var cumulative = 0.0
                    val target = normalized / 360.0
                    selectedIndex = slices.indexOfFirst { slice ->
                        cumulative += slice.share
                        target < cumulative
                    }
                } else {
                    selectedIndex = -1
                }
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            animator?.cancel()
            reveal = 1f
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            animator?.cancel()
            reveal = 1f
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun textPaint(
        sp: Float,
        color: Int,
        medium: Boolean = false,
        tabular: Boolean = false
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        this.color = color
        typeface = android.graphics.Typeface.create(
            if (medium) "sans-serif-medium" else "sans-serif",
            android.graphics.Typeface.NORMAL
        )
        if (tabular) fontFeatureSettings = "tnum"
    }

    private fun dp(value: Float): Float = value * density
}
