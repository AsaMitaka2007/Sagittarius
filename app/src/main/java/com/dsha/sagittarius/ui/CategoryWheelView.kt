package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.util.Money
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 单条连续轨道类别轮盘：
 *
 * 段 A 是左下 30° 起、顺时针绕过 270° 到右下 30° 的圆弧；
 * 段 B 从圆弧末端沿切线向右伸出屏幕。所有类别按固定间距排在
 * 这条 Path 上，第 9 个以后会进入段 B 的右侧隐藏区。
 */
class CategoryWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val scroller = Scroller(context)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
        strokeCap = Paint.Cap.ROUND
        color = ColorUtils.setAlphaComponent(tokens.textTertiary, 78)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val iconCache = mutableMapOf<Int, Drawable>()
    private val badgeRect = RectF()
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private val arcRect = RectF()
    private val arcPath = Path()
    private val trackPath = Path()
    private val arcMeasure = PathMeasure()
    private val trackMeasure = PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    private var categories: List<CategoryEntity> = emptyList()
    private var recordedAmounts: Map<Long, Long> = emptyMap()
    private var arcLength = 0f
    private var totalLength = 0f
    private var slotSpacing = 0f
    private var maxOffset = 0f
    private var offsetDistance = 0f
    private var pressedIndex = -1
    private var selectedCategoryId: Long? = null
    private var lastEdgeHapticAt = 0L

    var lastTapX = 0f
        private set
    var lastTapY = 0f
        private set

    private var downX = 0f
    private var downY = 0f
    private var downBallIndex = -1
    private var moved = false
    private var longPressReady = false
    private var dragSwap = false
    private var rotating = false
    private var dragX = 0f
    private var dragY = 0f
    private var targetIndex = -1
    private var lastAngle = 0f
    private var lastMoveTime = 0L

    private var offsetVelocity = 0f

    var onCategoryClick: ((CategoryEntity) -> Unit)? = null
    var onCategoryLongPress: ((CategoryEntity) -> Unit)? = null
    var onSwapOrder: ((CategoryEntity, CategoryEntity) -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (!moved && downBallIndex in categories.indices) {
            longPressReady = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    init {
        isClickable = true
        contentDescription = "类别轨道轮盘，拖动滑动，点击类别累加金额"
    }

    fun setCategories(list: List<CategoryEntity>) {
        categories = list.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        if (categories.isEmpty()) {
            offsetDistance = 0f
            pressedIndex = -1
        }
        updateOffsetBounds()
        invalidate()
    }

    fun setRecordedAmounts(amounts: Map<Long, Long>) {
        recordedAmounts = amounts
        invalidate()
    }

    fun setSelectedCategory(categoryId: Long?) {
        selectedCategoryId = categoryId
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildTrack()
    }

    private fun buildTrack() {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(36f)
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        arcPath.reset()
        arcPath.addArc(arcRect, 120f, 270f)
        arcMeasure.setPath(arcPath, false)
        arcLength = arcMeasure.length

        arcMeasure.getPosTan(arcLength, pos, tan)
        // 从右下端点继续向右侧伸出屏幕；视觉上作为轨道的直线延伸段。
        val lineLength = width.toFloat() + dp(120f)
        val lineEndX = pos[0] + lineLength
        val lineEndY = pos[1]

        trackPath.reset()
        trackPath.addArc(arcRect, 120f, 270f)
        trackPath.lineTo(lineEndX, lineEndY)
        trackMeasure.setPath(trackPath, false)
        totalLength = trackMeasure.length

        slotSpacing = arcLength / 8f
        updateOffsetBounds()
    }

    private fun updateOffsetBounds() {
        if (slotSpacing <= 0f) return
        maxOffset = ((categories.size - 1).coerceAtLeast(0) * slotSpacing).coerceAtLeast(0f)
        offsetDistance = offsetDistance.coerceIn(0f, maxOffset)
    }

    private fun ballRadius(): Float = dp(21f)

    private fun distanceFor(index: Int): Float = index * slotSpacing - offsetDistance

    private fun alphaFor(index: Int, x: Float): Int {
        val distance = distanceFor(index)
        if (distance < 0f || distance > totalLength) return 0
        var alpha = 1f
        if (distance > arcLength - slotSpacing * 0.42f) {
            alpha *= (1f - (distance - (arcLength - slotSpacing * 0.42f)) / (slotSpacing * 0.9f))
                .coerceIn(0f, 1f)
        }
        val rightEdge = width - dp(22f)
        if (x > rightEdge) {
            alpha *= (1f - (x - rightEdge) / dp(50f)).coerceIn(0f, 1f)
        }
        return (255 * alpha).roundToInt().coerceIn(0, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trackMeasure.length <= 0f) return
        canvas.drawPath(trackPath, linePaint)

        categories.forEachIndexed { index, category ->
            if (dragSwap && index == downBallIndex) return@forEachIndexed
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEachIndexed
            trackMeasure.getPosTan(distance, pos, tan)
            val alpha = alphaFor(index, pos[0])
            if (alpha <= 12) return@forEachIndexed
            drawBall(canvas, category, index, pos[0], pos[1], alpha)
        }

        if (dragSwap && downBallIndex in categories.indices) {
            drawBall(canvas, categories[downBallIndex], downBallIndex, dragX, dragY, 255, isDrag = true)
            if (targetIndex in categories.indices) {
                positionOf(targetIndex)?.let { (x, y) ->
                    borderPaint.color = tokens.gradientMid
                    borderPaint.strokeWidth = 2f * density
                    borderPaint.alpha = 255
                    canvas.drawCircle(x, y, ballRadius() + dp(6f), borderPaint)
                }
            }
        }
        drawRecordedBadges(canvas)
    }

    private fun positionOf(index: Int): Pair<Float, Float>? {
        val distance = distanceFor(index)
        if (distance < 0f || distance > totalLength) return null
        trackMeasure.getPosTan(distance, pos, tan)
        return pos[0] to pos[1]
    }

    private fun drawBall(
        canvas: Canvas,
        category: CategoryEntity,
        index: Int,
        x: Float,
        y: Float,
        alpha: Int,
        isDrag: Boolean = false
    ) {
        val radius = if (isDrag) dp(26f) else ballRadius()
        if (x < -radius || x > width + radius || y < -radius || y > height + radius) return
        val base = CategoryCatalog.defaultColor(context, category)
        val light = ColorUtils.blendARGB(base, Color.WHITE, 0.26f)
        val dark = ColorUtils.blendARGB(base, Color.BLACK, 0.22f)
        val pressed = index == pressedIndex
        val recorded = recordedAmounts[category.id] ?: 0L
        val selected = category.id == selectedCategoryId

        if (selected) {
            borderPaint.color = ColorUtils.setAlphaComponent(tokens.gradientStart, 210)
            borderPaint.strokeWidth = 4f * density
            borderPaint.alpha = alpha
            canvas.drawCircle(x, y, radius + dp(7f), borderPaint)
        }
        if (recorded > 0L) {
            borderPaint.color = ColorUtils.setAlphaComponent(base, 120)
            borderPaint.strokeWidth = 3f * density
            borderPaint.alpha = alpha
            canvas.drawCircle(x, y, radius + dp(5f), borderPaint)
        }
        if (pressed || isDrag) {
            borderPaint.color = ColorUtils.setAlphaComponent(tokens.gradientStart, 180)
            borderPaint.strokeWidth = 4f * density
            borderPaint.alpha = alpha
            canvas.drawCircle(x, y, radius + dp(3f), borderPaint)
        }

        fillPaint.alpha = alpha
        fillPaint.shader = RadialGradient(
            x - radius * 0.32f,
            y - radius * 0.4f,
            radius * 1.35f,
            intArrayOf(light, base, dark),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, fillPaint)
        fillPaint.shader = null

        borderPaint.alpha = alpha
        borderPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 70)
        borderPaint.strokeWidth = 1f * density
        canvas.drawCircle(x, y, radius, borderPaint)

        val textColor = if (ColorUtils.calculateLuminance(base) > 0.58) {
            Color.parseColor("#172033")
        } else {
            Color.WHITE
        }
        if (radius >= dp(15f)) {
            val iconSize = radius * 0.82f
            val iconRes = CategoryCatalog.iconRes(category.iconKey)
            val drawable = iconCache.getOrPut(iconRes) {
                ContextCompat.getDrawable(context, iconRes)?.mutate()
                    ?: ContextCompat.getDrawable(context, com.dsha.sagittarius.R.drawable.ic_category_other)!!
            }
            drawable.setTint(textColor)
            drawable.alpha = alpha
            drawable.setBounds(
                (x - iconSize / 2f).roundToInt(),
                (y - radius * 0.58f).roundToInt(),
                (x + iconSize / 2f).roundToInt(),
                (y - radius * 0.58f + iconSize).roundToInt()
            )
            drawable.draw(canvas)

            namePaint.alpha = alpha
            namePaint.color = textColor
            namePaint.textSize = sp(7.6f)
            val name = fitName(CategoryCatalog.displayName(context, category), radius * 1.55f)
            canvas.drawText(name, x, y + radius * 0.62f, namePaint)
        }
    }

    private fun drawRecordedBadges(canvas: Canvas) {
        categories.forEachIndexed { index, category ->
            val amount = recordedAmounts[category.id] ?: 0L
            if (amount <= 0L) return@forEachIndexed
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEachIndexed
            trackMeasure.getPosTan(distance, pos, tan)
            val alpha = alphaFor(index, pos[0])
            if (alpha <= 30) return@forEachIndexed
            val x = pos[0]
            val y = pos[1]
            val text = "已记 ${Money.formatCents(amount)}"
            badgeTextPaint.textSize = sp(7.2f)
            val width = badgeTextPaint.measureText(text) + dp(10f)
            val height = dp(14f)
            val top = y + ballRadius() + dp(7f)
            if (top + height > this.height + dp(4f)) return@forEachIndexed
            badgeRect.set(x - width / 2f, top, x + width / 2f, top + height)
            badgeBgPaint.color = ColorUtils.setAlphaComponent(tokens.cardFillBottom, 235)
            badgeBgPaint.alpha = alpha
            canvas.drawRoundRect(badgeRect, height / 2f, height / 2f, badgeBgPaint)
            badgeTextPaint.alpha = alpha
            badgeTextPaint.color = tokens.textPrimary
            canvas.drawText(text, x, top + height * 0.72f, badgeTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scroller.forceFinished(true)
                downX = event.x
                downY = event.y
                lastAngle = angleAt(event.x, event.y)
                lastMoveTime = event.eventTime
                offsetVelocity = 0f
                downBallIndex = findBall(event.x, event.y)
                moved = false
                longPressReady = false
                dragSwap = false
                rotating = false
                targetIndex = -1
                dragX = event.x
                dragY = event.y
                if (downBallIndex >= 0) {
                    handler.postDelayed(longPressRunnable, 500L)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(event.x - downX, event.y - downY)
                if (longPressReady && downBallIndex in categories.indices) {
                    if (distance > touchSlop) {
                        if (!dragSwap) {
                            dragSwap = true
                            rotating = false
                        }
                        dragX = event.x
                        dragY = event.y
                        targetIndex = findNearestBall(event.x, event.y, downBallIndex)
                        invalidate()
                    }
                    return true
                }
                if (!moved && distance > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                }
                if (moved) {
                    rotating = true
                    val angle = angleAt(event.x, event.y)
                    val delta = shortestDelta(angle - lastAngle)
                    // 逆时针为正，向右拉出直线段类别；顺时针为负，0 处卡死。
                    val ccw = -delta
                    val distanceDelta = Math.toRadians(ccw.toDouble()).toFloat() * arcRadius()
                    val before = offsetDistance
                    val raw = offsetDistance + distanceDelta
                    offsetDistance = when {
                        raw < 0f -> raw * 0.18f
                        raw > maxOffset -> maxOffset + (raw - maxOffset) * 0.18f
                        else -> raw
                    }
                    if (raw < 0f || raw > maxOffset) {
                        val now = System.currentTimeMillis()
                        if (now - lastEdgeHapticAt > 220L) {
                            lastEdgeHapticAt = now
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                    val actual = offsetDistance - before
                    val dt = (event.eventTime - lastMoveTime).coerceAtLeast(1L)
                    val instant = actual / dt * 1000f
                    offsetVelocity = offsetVelocity * 0.72f + instant * 0.28f
                    lastMoveTime = event.eventTime
                    lastAngle = angle
                    postInvalidateOnAnimation()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (dragSwap && downBallIndex in categories.indices &&
                        targetIndex in categories.indices && targetIndex != downBallIndex
                    ) {
                        onSwapOrder?.invoke(categories[downBallIndex], categories[targetIndex])
                    } else if (longPressReady && downBallIndex in categories.indices) {
                        onCategoryLongPress?.invoke(categories[downBallIndex])
                    } else if (!moved && downBallIndex in categories.indices) {
                        pressedIndex = downBallIndex
                        lastTapX = downX
                        lastTapY = downY
                        onCategoryClick?.invoke(categories[downBallIndex])
                        invalidate()
                        handler.postDelayed({ pressedIndex = -1; invalidate() }, 150L)
                    } else if (moved) {
                        if (offsetDistance < 0f || offsetDistance > maxOffset) {
                            settleEdge()
                        } else if (abs(offsetVelocity) > dp(18f)) {
                            scroller.fling(
                                offsetDistance.roundToInt().coerceIn(0, maxOffset.roundToInt()),
                                0,
                                offsetVelocity.roundToInt(),
                                0,
                                0,
                                maxOffset.roundToInt(),
                                0,
                                0
                            )
                            postInvalidateOnAnimation()
                        }
                    }
                }
                downBallIndex = -1
                targetIndex = -1
                longPressReady = false
                dragSwap = false
                rotating = false
                moved = false
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun settleEdge() {
        val target = offsetDistance.coerceIn(0f, maxOffset)
        ValueAnimator.ofFloat(offsetDistance, target).apply {
            duration = 220L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                offsetDistance = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            offsetDistance = scroller.currX.toFloat().coerceIn(0f, maxOffset)
            postInvalidateOnAnimation()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        scroller.forceFinished(true)
        super.onDetachedFromWindow()
    }

    private fun arcRadius(): Float {
        val cx = width / 2f
        return (arcRect.right - cx).coerceAtLeast(1f)
    }

    private fun arcRadiusRaw(): Float = arcRadius()

    private fun findBall(x: Float, y: Float): Int {
        if (categories.isEmpty()) return -1
        var best = -1
        var bestDistance = Float.MAX_VALUE
        categories.indices.forEach { index ->
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEach
            trackMeasure.getPosTan(distance, pos, tan)
            if (alphaFor(index, pos[0]) < 30) return@forEach
            val d = hypot(x - pos[0], y - pos[1])
            if (d < bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return if (bestDistance <= ballRadius() + dp(10f)) best else -1
    }

    private fun findNearestBall(x: Float, y: Float, exclude: Int): Int {
        if (categories.isEmpty()) return -1
        var best = -1
        var bestDistance = Float.MAX_VALUE
        categories.indices.forEach { index ->
            if (index == exclude) return@forEach
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEach
            trackMeasure.getPosTan(distance, pos, tan)
            if (alphaFor(index, pos[0]) < 30) return@forEach
            val d = hypot(x - pos[0], y - pos[1])
            if (d < bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return best
    }

    private fun angleAt(x: Float, y: Float): Float {
        val cx = width / 2f
        val cy = height / 2f
        return Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
    }

    private fun shortestDelta(value: Float): Float {
        var v = value % 360f
        if (v > 180f) v -= 360f
        if (v < -180f) v += 360f
        return v
    }

    private fun fitName(name: String, maxWidth: Float): String {
        if (namePaint.measureText(name) <= maxWidth) return name
        var end = name.length
        while (end > 1 && namePaint.measureText(name.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return name.substring(0, end) + "…"
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
