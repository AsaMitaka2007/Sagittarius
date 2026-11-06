package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** 自绘图片裁剪视图：单指拖动、双指缩放，裁剪框按目标比例固定。 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f * density
        color = Color.argb(100, 255, 255, 255)
    }
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val crop = RectF()
    private val imageBounds = RectF()
    private val bitmapRect = RectF()
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            matrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
            val bmp = bitmap
            if (bmp != null && crop.width() > 0f) {
                bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                matrix.mapRect(imageBounds, bitmapRect)
                if (imageBounds.width() < crop.width() || imageBounds.height() < crop.height()) {
                    val fit = max(crop.width() / imageBounds.width(), crop.height() / imageBounds.height())
                    matrix.postScale(fit, fit, detector.focusX, detector.focusY)
                }
            }
            clamp()
            invalidate()
            return true
        }
    })

    private var bitmap: Bitmap? = null
    private var aspect = 3f
    private var lastX = 0f
    private var lastY = 0f
    private var initialized = false

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        initialized = false
        requestLayout()
        invalidate()
    }

    fun setAspectRatio(x: Int, y: Int) {
        aspect = x.toFloat() / y.coerceAtLeast(1)
        initialized = false
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initialized = false
    }

    private fun initMatrix() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0 || initialized) return
        val maxW = width - dp(32f)
        val maxH = height - dp(110f)
        var cropW = maxW
        var cropH = cropW / aspect
        if (cropH > maxH) {
            cropH = maxH
            cropW = cropH * aspect
        }
        val cx = width / 2f
        val cy = height / 2f
        crop.set(cx - cropW / 2f, cy - cropH / 2f, cx + cropW / 2f, cy + cropH / 2f)
        val base = max(cropW / bmp.width, cropH / bmp.height)
        matrix.reset()
        matrix.postScale(base, base)
        matrix.postTranslate(cx - bmp.width * base / 2f, cy - bmp.height * base / 2f)
        clamp()
        initialized = true
    }

    fun reset() {
        initialized = false
        initMatrix()
        invalidate()
    }

    private fun clamp() {
        val bmp = bitmap ?: return
        bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        matrix.mapRect(imageBounds, bitmapRect)
        var dx = 0f
        var dy = 0f
        if (imageBounds.left > crop.left) dx = crop.left - imageBounds.left
        if (imageBounds.right < crop.right) dx = crop.right - imageBounds.right
        if (imageBounds.top > crop.top) dy = crop.top - imageBounds.top
        if (imageBounds.bottom < crop.bottom) dy = crop.bottom - imageBounds.bottom
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        initMatrix()
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, matrix, bitmapPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), crop.top, dimPaint)
        canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, dimPaint)
        canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, dimPaint)
        canvas.drawRect(crop, borderPaint)
        for (i in 1..2) {
            val x = crop.left + crop.width() * i / 3f
            val y = crop.top + crop.height() * i / 3f
            canvas.drawLine(x, crop.top, x, crop.bottom, gridPaint)
            canvas.drawLine(crop.left, y, crop.right, y, gridPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    matrix.postTranslate(event.x - lastX, event.y - lastY)
                    clamp()
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
                return true
            }
        }
        return true
    }

    /** 返回按裁剪框截取出的位图；失败时返回 null。 */
    fun cropBitmap(): Bitmap? {
        val bmp = bitmap ?: return null
        initMatrix()
        matrix.invert(inverse)
        val src = RectF()
        inverse.mapRect(src, crop)
        val left = src.left.toInt().coerceIn(0, bmp.width - 1)
        val top = src.top.toInt().coerceIn(0, bmp.height - 1)
        val right = src.right.toInt().coerceIn(left + 1, bmp.width)
        val bottom = src.bottom.toInt().coerceIn(top + 1, bmp.height)
        return runCatching {
            Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
        }.getOrNull()
    }

    private fun dp(value: Float): Float = value * density
}
