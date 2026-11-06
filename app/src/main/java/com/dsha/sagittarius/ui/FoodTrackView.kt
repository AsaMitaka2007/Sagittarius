package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.data.FoodEntity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 饮食食材轮盘。路径、惯性和角度拖动完全对齐 CategoryWheelView：
 * 开口圆环 + 右侧直线延伸，PathMeasure 定位，长按移除/拖动排序。
 */
class FoodTrackView @JvmOverloads constructor(
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
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 7.2f * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val arcRect = RectF()
    private val arcPath = Path()
    private val trackPath = Path()
    private val arcMeasure = PathMeasure()
    private val trackMeasure = PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    private var foods: List<FoodEntity> = emptyList()
    private var arcLength = 0f
    private var totalLength = 0f
    private var slotSpacing = 0f
    private var maxOffset = 0f
    private var offsetDistance = 0f
    private var offsetVelocity = 0f
    private var pressedIndex = -1

    private var downX = 0f
    private var downY = 0f
    private var downBallIndex = -1
    private var moved = false
    private var longPressReady = false
    private var dragSwap = false
    private var targetIndex = -1
    private var lastAngle = 0f
    private var lastMoveTime = 0L

    var onFoodClick: ((FoodEntity) -> Unit)? = null
    var onFoodLongPress: ((FoodEntity) -> Unit)? = null
    var onSwapOrder: ((FoodEntity, FoodEntity) -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (!moved && downBallIndex in foods.indices) {
            longPressReady = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    init {
        isClickable = true
        contentDescription = "食材轮盘，拖动浏览，点击食材记录，长按移除或拖动排序"
    }

    fun favoriteSnapshot(): List<FoodEntity> = foods

    fun setFoods(list: List<FoodEntity>) {
        foods = list.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        if (foods.isEmpty()) {
            offsetDistance = 0f
            pressedIndex = -1
        }
        updateOffsetBounds()
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
        val lineEndX = pos[0] + width + dp(120f)
        trackPath.reset()
        trackPath.addArc(arcRect, 120f, 270f)
        trackPath.lineTo(lineEndX, pos[1])
        trackMeasure.setPath(trackPath, false)
        totalLength = trackMeasure.length
        slotSpacing = arcLength / 8f
        updateOffsetBounds()
    }

    private fun updateOffsetBounds() {
        if (slotSpacing <= 0f) return
        maxOffset = ((foods.size - 1).coerceAtLeast(0) * slotSpacing).coerceAtLeast(0f)
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
        if (x > rightEdge) alpha *= (1f - (x - rightEdge) / dp(50f)).coerceIn(0f, 1f)
        return (255 * alpha).roundToInt().coerceIn(0, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalLength <= 0f) return
        canvas.drawPath(trackPath, linePaint)
        foods.forEachIndexed { index, food ->
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEachIndexed
            trackMeasure.getPosTan(distance, pos, tan)
            val alpha = alphaFor(index, pos[0])
            if (alpha <= 0) return@forEachIndexed
            drawFood(canvas, food, index, pos[0], pos[1], alpha)
        }
    }

    private fun drawFood(canvas: Canvas, food: FoodEntity, index: Int, x: Float, y: Float, alpha: Int) {
        val radius = ballRadius()
        val base = ColorUtils.blendARGB(tokens.gradientStart, tokens.gradientEnd, (index % 5) / 5f)
        fillPaint.color = base
        fillPaint.alpha = alpha
        canvas.drawCircle(x, y, radius, fillPaint)
        val bitmap: Bitmap? = food.imageUri?.let { FoodImageLoader.load(it) }
        if (bitmap != null) {
            clipPath.reset()
            clipPath.addCircle(x, y, radius - dp(1f), Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clipPath)
            srcRect.set(0, 0, bitmap.width, bitmap.height)
            dstRect.set(x - radius, y - radius, x + radius, y + radius)
            imagePaint.alpha = alpha
            canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)
            canvas.restoreToCount(save)
        } else {
            iconPaint.alpha = alpha
            iconPaint.textSize = dp(18f)
            iconPaint.color = Color.WHITE
            canvas.drawText(food.icon.take(2), x, y - dp(1f), iconPaint)
        }
        borderPaint.alpha = alpha
        borderPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 90)
        borderPaint.strokeWidth = 1f * density
        canvas.drawCircle(x, y, radius, borderPaint)
        namePaint.alpha = alpha
        namePaint.color = Color.WHITE
        canvas.drawText(fitName(food.name), x, y + dp(14f), namePaint)
        if (pressedIndex == index && longPressReady) {
            borderPaint.alpha = 255
            borderPaint.color = Color.WHITE
            borderPaint.strokeWidth = dp(2f)
            canvas.drawCircle(x, y, radius + dp(2f), borderPaint)
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
                targetIndex = -1
                if (downBallIndex >= 0) {
                    pressedIndex = downBallIndex
                    handler.postDelayed(longPressRunnable, 500L)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(event.x - downX, event.y - downY)
                if (longPressReady && downBallIndex in foods.indices) {
                    if (distance > touchSlop) {
                        if (!dragSwap) {
                            dragSwap = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        targetIndex = findNearestBall(event.x, event.y, downBallIndex)
                        invalidate()
                    }
                    return true
                }
                if (!moved && distance > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                    pressedIndex = -1
                }
                if (moved) {
                    val angle = angleAt(event.x, event.y)
                    val delta = shortestDelta(angle - lastAngle)
                    val ccw = -delta
                    val distanceDelta = Math.toRadians(ccw.toDouble()).toFloat() * arcRadius()
                    val before = offsetDistance
                    offsetDistance = (offsetDistance + distanceDelta).coerceIn(0f, maxOffset)
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
                    if (dragSwap && downBallIndex in foods.indices &&
                        targetIndex in foods.indices && targetIndex != downBallIndex
                    ) {
                        onSwapOrder?.invoke(foods[downBallIndex], foods[targetIndex])
                    } else if (longPressReady && downBallIndex in foods.indices) {
                        onFoodLongPress?.invoke(foods[downBallIndex])
                    } else if (!moved && downBallIndex in foods.indices) {
                        pressedIndex = downBallIndex
                        onFoodClick?.invoke(foods[downBallIndex])
                        handler.postDelayed({ pressedIndex = -1; invalidate() }, 150L)
                    } else if (moved && abs(offsetVelocity) > dp(18f)) {
                        scroller.fling(
                            offsetDistance.roundToInt().coerceIn(0, maxOffset.roundToInt()),
                            0,
                            offsetVelocity.roundToInt(),
                            0, 0, maxOffset.roundToInt(), 0, 0
                        )
                        postInvalidateOnAnimation()
                    }
                }
                downBallIndex = -1
                targetIndex = -1
                longPressReady = false
                dragSwap = false
                moved = false
                pressedIndex = -1
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
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

    private fun arcRadius(): Float = (min(width, height) / 2f - dp(36f)).coerceAtLeast(1f)

    private fun angleAt(x: Float, y: Float): Float {
        val dx = x - width / 2f
        val dy = y - height / 2f
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    private fun shortestDelta(delta: Float): Float {
        var d = delta
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    private fun findBall(x: Float, y: Float): Int {
        if (totalLength <= 0f) return -1
        foods.forEachIndexed { index, _ ->
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEachIndexed
            trackMeasure.getPosTan(distance, pos, tan)
            if (hypot(x - pos[0], y - pos[1]) <= dp(27f)) return index
        }
        return -1
    }

    private fun findNearestBall(x: Float, y: Float, exclude: Int): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        foods.forEachIndexed { index, _ ->
            if (index == exclude) return@forEachIndexed
            val distance = distanceFor(index)
            if (distance < 0f || distance > totalLength) return@forEachIndexed
            trackMeasure.getPosTan(distance, pos, tan)
            val d = hypot(x - pos[0], y - pos[1])
            if (d < bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return if (bestDistance <= dp(44f)) best else -1
    }

    private fun fitName(name: String): String {
        val maxWidth = dp(38f)
        if (namePaint.measureText(name) <= maxWidth) return name
        var end = name.length
        while (end > 1 && namePaint.measureText(name.substring(0, end) + "…") > maxWidth) end--
        return name.substring(0, end) + "…"
    }

    private fun dp(value: Float): Float = value * density
}
