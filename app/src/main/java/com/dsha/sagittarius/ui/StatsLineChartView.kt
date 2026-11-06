package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.R
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * 日销折线图：按月分页，可无限左右拖动，松手吸附到最近整月。
 * 只绘制当前页及相邻页；拖动时隐藏 tooltip。
 */
class StatsLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val lowEnd = MotionUtils.isLowRam(context)
    private val reduceMotion = MotionUtils.reduceMotion(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = ColorUtils.setAlphaComponent(tokens.divider, 90)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ColorUtils.setAlphaComponent(tokens.textPrimary, 180)
    }
    private val axisPaint = textPaint(9f, tokens.textTertiary)
    private val labelPaint = textPaint(11f, tokens.textSecondary)
    private val emptyPaint = textPaint(11f, tokens.textTertiary)
    private val tooltipTitlePaint = textPaint(10f, tokens.textSecondary)
    private val tooltipAmountPaint = textPaint(13f, tokens.textPrimary, medium = true, tabular = true)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = tokens.cardStrokeStart
    }
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cardRect = RectF()

    private var provider: ((YearMonth) -> List<DailyPoint>)? = null
    private var anchorMonth: YearMonth = YearMonth.now()
    private var focusedPage = 0
    private var scrollX = 0
    private var selectedPage = -1
    private var selectedIndex = -1
    private var reveal = 1f
    private var revealAnimator: ValueAnimator? = null
    private val cache = mutableMapOf<YearMonth, List<DailyPoint>>()
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchX = 0f
    private var downX = 0f
    private var moved = false
    private var settling = false
    private var needsSnap = false
    private var locked = false

    private val visiblePages = mutableListOf<Int>()

    var onMonthChanged: ((YearMonth) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "日销折线图，可左右拖动切换月份"
        emptyPaint.textAlign = Paint.Align.CENTER
    }

    fun setDataProvider(
        dataProvider: (YearMonth) -> List<DailyPoint>,
        focusMonth: YearMonth = YearMonth.now()
    ) {
        provider = dataProvider
        anchorMonth = focusMonth
        scrollX = 0
        focusedPage = 0
        selectedPage = -1
        selectedIndex = -1
        cache.clear()
        notifyMonth()
        startReveal()
        invalidate()
    }

    fun setFocusMonth(month: YearMonth) {
        scroller.abortAnimation()
        settling = false
        needsSnap = false
        anchorMonth = month
        scrollX = 0
        focusedPage = 0
        selectedPage = -1
        selectedIndex = -1
        notifyMonth()
        startReveal()
        invalidate()
    }

    fun refreshData() {
        cache.clear()
        selectedPage = -1
        selectedIndex = -1
        invalidate()
    }

    fun setLocked(value: Boolean) {
        locked = value
        if (value) {
            scroller.abortAnimation()
            settling = false
            needsSnap = false
        }
        invalidate()
    }

    private fun startReveal() {
        revealAnimator?.cancel()
        if (!isAttachedToWindow || !isShown || lowEnd || reduceMotion) {
            reveal = 1f
            return
        }
        reveal = 0f
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize((320f * density).toInt(), widthMeasureSpec)
        val height = resolveSize((220f * density).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScrim(canvas)
        val dataProvider = provider ?: run {
            drawEmptyOnCurrentPage(canvas)
            return
        }
        val pageWidth = width.toFloat()
        if (pageWidth <= 0f) return

        visiblePages.clear()
        val first = floor(scrollX / pageWidth).toInt() - 1
        val last = ceil((scrollX + pageWidth) / pageWidth).toInt() + 1
        for (page in first..last) {
            val pageLeft = page * pageWidth - scrollX
            if (pageLeft + pageWidth < 0f || pageLeft > pageWidth) continue
            visiblePages += page
            val month = anchorMonth.plusMonths(page.toLong())
            val points = cache.getOrPut(month) { dataProvider(month) }
            drawPage(canvas, page, pageLeft, month, points)
        }
        if (selectedPage in visiblePages && selectedIndex >= 0) {
            drawSelectedTooltip(canvas)
        }
    }

    private fun drawPage(
        canvas: Canvas,
        page: Int,
        pageLeft: Float,
        month: YearMonth,
        points: List<DailyPoint>
    ) {
        val pageWidth = width.toFloat()
        val left = pageLeft + dp(46f)
        val right = pageLeft + pageWidth - dp(12f)
        val top = dp(20f)
        val bottom = height - dp(28f)
        val chartHeight = bottom - top
        val chartWidth = right - left
        if (chartWidth <= 0f || chartHeight <= 0f) return

        if (points.isEmpty() || points.all { it.amountCents <= 0L }) {
            emptyPaint.color = tokens.textTertiary
            canvas.drawText(
                context.getString(R.string.stats_month_empty),
                pageLeft + pageWidth / 2f,
                top + chartHeight / 2f,
                emptyPaint
            )
            return
        }

        val maxAmount = points.maxOf { it.amountCents }.coerceAtLeast(1L)
        val yMax = niceMax(maxAmount)
        val gridLines = 4
        for (i in 0..gridLines) {
            val ratio = i.toFloat() / gridLines
            val y = bottom - chartHeight * ratio
            canvas.drawLine(left, y, right, y, gridPaint)
            axisPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(axisValue((yMax * ratio).toLong()), left - dp(6f), y + dp(3f), axisPaint)
        }

        val lastIndex = (points.size - 1).coerceAtLeast(1)
        val pageReveal = if (page == focusedPage) reveal else 1f
        val labelDays = buildList {
            add(1)
            for (day in listOf(5, 10, 15, 20, 25)) if (day < points.size) add(day)
            add(points.size)
        }.distinct()
        var lastLabelRight = Float.NEGATIVE_INFINITY
        axisPaint.textAlign = Paint.Align.CENTER
        labelDays.forEach { day ->
            val x = left + chartWidth * (day - 1) / lastIndex
            val text = day.toString()
            val half = axisPaint.measureText(text) / 2f
            val labelLeft = x - half
            if (labelLeft - lastLabelRight >= dp(8f)) {
                canvas.drawText(text, x, bottom + dp(16f), axisPaint)
                lastLabelRight = x + half
            }
        }

        path.reset()
        val pageDots = mutableListOf<Pair<Float, Float>>()
        points.forEachIndexed { index, point ->
            val x = left + chartWidth * index / lastIndex
            val targetY = bottom - (point.amountCents.toFloat() / yMax) * chartHeight
            val y = bottom - (bottom - targetY) * pageReveal
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            pageDots += x to y
        }
        val lineStart = if (ThemeManager.isLight) tokens.titleStart else tokens.gradientStart
        val lineMid = if (ThemeManager.isLight) tokens.titleMid else tokens.gradientMid
        val lineEnd = if (ThemeManager.isLight) tokens.titleEnd else tokens.gradientEnd
        linePaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(lineStart, lineMid, lineEnd),
            null,
            Shader.TileMode.CLAMP
        )
        linePaint.alpha = (pageReveal * 255).toInt()
        canvas.drawPath(path, linePaint)
        linePaint.shader = null

        pageDots.forEachIndexed { index, value ->
            val selected = page == selectedPage && index == selectedIndex
            val radius = if (selected) dp(5f) else dp(2.8f)
            dotPaint.alpha = (pageReveal * 255).toInt()
            dotPaint.color = when {
                selected -> tokens.accent
                ThemeManager.isLight -> tokens.titleStart
                else -> tokens.gradientStart
            }
            canvas.drawCircle(value.first, value.second, radius, dotPaint)
            if (selected) {
                dotRingPaint.alpha = (pageReveal * 255).toInt()
                canvas.drawCircle(value.first, value.second, radius + dp(2f), dotRingPaint)
            }
        }
    }

    private fun drawSelectedTooltip(canvas: Canvas) {
        val pageWidth = width.toFloat()
        val pageLeft = selectedPage * pageWidth - scrollX
        val month = anchorMonth.plusMonths(selectedPage.toLong())
        val points = cache[month] ?: return
        if (selectedIndex !in points.indices) return
        val point = points[selectedIndex]
        val left = pageLeft + dp(46f)
        val right = pageLeft + pageWidth - dp(12f)
        val top = dp(20f)
        val bottom = height - dp(28f)
        val chartWidth = right - left
        val chartHeight = bottom - top
        val lastIndex = (points.size - 1).coerceAtLeast(1)
        val x = left + chartWidth * selectedIndex / lastIndex
        val maxAmount = points.maxOf { it.amountCents }.coerceAtLeast(1L)
        val yMax = niceMax(maxAmount)
        val targetY = bottom - (point.amountCents.toFloat() / yMax) * chartHeight
        val y = bottom - (bottom - targetY) * if (selectedPage == focusedPage) reveal else 1f

        val title = DateText.short(point.date)
        val amount = Money.formatCents(point.amountCents)
        val contentWidth = max(tooltipTitlePaint.measureText(title), tooltipAmountPaint.measureText(amount))
        val cardWidth = contentWidth + dp(20f)
        val cardHeight = dp(46f)
        val cardLeft = (x - cardWidth / 2f).coerceIn(left, right - cardWidth)
        var cardTop = y - cardHeight - dp(12f)
        if (cardTop < top) cardTop = y + dp(12f)
        if (cardTop + cardHeight > bottom) cardTop = bottom - cardHeight
        cardRect.set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        cardPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillTop, 245)
        canvas.drawRoundRect(cardRect, dp(10f), dp(10f), cardPaint)
        canvas.drawRoundRect(cardRect, dp(10f), dp(10f), cardStrokePaint)
        canvas.drawText(title, cardRect.left + dp(10f), cardRect.top + dp(17f), tooltipTitlePaint)
        canvas.drawText(amount, cardRect.left + dp(10f), cardRect.top + dp(35f), tooltipAmountPaint)
    }

    private fun drawEmptyOnCurrentPage(canvas: Canvas) {
        emptyPaint.color = tokens.textTertiary
        canvas.drawText(
            context.getString(R.string.stats_month_empty),
            width / 2f,
            height / 2f,
            emptyPaint
        )
    }

    private fun drawScrim(canvas: Canvas) {
        if (!ThemeManager.isGlass) return
        scrimPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillBottom, 24)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scroller.abortAnimation()
                settling = false
                needsSnap = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                lastTouchX = event.x
                downX = event.x
                moved = false
                selectedPage = -1
                selectedIndex = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = lastTouchX - event.x
                if (abs(event.x - downX) > touchSlop) moved = true
                if (moved) {
                    scrollX = (scrollX + dx.toInt()).coerceIn(-maxScroll(), maxScroll())
                    lastTouchX = event.x
                    updateFocusedMonth()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.addMovement(event)
                if (!moved) {
                    handleTap(event.x, event.y)
                } else {
                    val tracker = velocityTracker
                    tracker?.computeCurrentVelocity(1000)
                    val velocityX = tracker?.xVelocity ?: 0f
                    if (abs(velocityX) > dp(150f)) {
                        needsSnap = true
                        scroller.fling(
                            scrollX,
                            0,
                            (-velocityX).toInt(),
                            0,
                            -maxScroll(),
                            maxScroll(),
                            0,
                            0
                        )
                    } else {
                        snapToNearest()
                    }
                    postInvalidateOnAnimation()
                }
                velocityTracker?.recycle()
                velocityTracker = null
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        val pageWidth = width.toFloat()
        if (pageWidth <= 0f) return
        val page = floor((scrollX + x) / pageWidth).toInt()
        val pageLeft = page * pageWidth - scrollX
        val left = pageLeft + dp(46f)
        val right = pageLeft + pageWidth - dp(12f)
        if (x < left || x > right) return
        val month = anchorMonth.plusMonths(page.toLong())
        val points = provider?.invoke(month) ?: return
        if (points.isEmpty()) return
        val index = Math.round((x - left) / (right - left) * points.lastIndex)
        selectedPage = page
        selectedIndex = index
        invalidate()
    }

    private fun snapToNearest() {
        val pageWidth = width.toFloat()
        if (pageWidth <= 0f) return
        val page = Math.round(scrollX / pageWidth)
        val target = (page * pageWidth).toInt()
        if (target == scrollX) {
            rebase(page)
            return
        }
        settling = true
        scroller.startScroll(scrollX, 0, target - scrollX, 0, 240)
        postInvalidateOnAnimation()
    }

    private fun rebase(page: Int) {
        if (page != 0) {
            anchorMonth = anchorMonth.plusMonths(page.toLong())
            scrollX = 0
            focusedPage = 0
            cache.clear()
            notifyMonth()
            invalidate()
        }
        settling = false
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.currX
            updateFocusedMonth()
            postInvalidateOnAnimation()
            return
        }
        if (settling) {
            settling = false
            rebase(Math.round(scrollX / width.toFloat()).toInt())
        } else if (needsSnap) {
            needsSnap = false
            snapToNearest()
        }
    }

    private fun updateFocusedMonth() {
        if (width <= 0) return
        val page = Math.round(scrollX / width.toFloat()).toInt()
        if (page != focusedPage) {
            focusedPage = page
            notifyMonth()
        }
    }

    private fun notifyMonth() {
        onMonthChanged?.invoke(anchorMonth.plusMonths(focusedPage.toLong()))
    }

    private fun maxScroll(): Int = (width.coerceAtLeast(1) * 5000)

    private fun niceMax(value: Long): Long {
        if (value <= 1000L) return 1000L
        val magnitude = Math.pow(10.0, floor(Math.log10(value.toDouble()))).toLong()
        return ((value + magnitude - 1) / magnitude) * magnitude
    }

    private fun axisValue(cents: Long): String {
        val yuan = cents / 100f
        return when {
            yuan >= 10000f -> String.format(java.util.Locale.CHINA, "%.1f万", yuan / 10000f)
            yuan >= 1000f -> String.format(java.util.Locale.CHINA, "%.1fk", yuan / 1000f)
            else -> String.format(java.util.Locale.CHINA, "%.0f", yuan)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            revealAnimator?.cancel()
            reveal = 1f
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            revealAnimator?.cancel()
            reveal = 1f
        }
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        revealAnimator = null
        scroller.abortAnimation()
        velocityTracker?.recycle()
        velocityTracker = null
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
