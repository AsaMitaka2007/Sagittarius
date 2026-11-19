package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.MotionUtils
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 竖向档位滑条（推理强度 / 生动度）。
 *
 * 轨道宽约 0.8cm，填充宽度略窄；从下往上由浅蓝过渡到蓝紫、紫色系，
 * 到最高档时轨道变深黑，填充切换为动态星空。拖动过程只预览，
 * 松手吸附到档位后才回调最终配置。
 */
class VerticalLevelSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val twinklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val fillRect = RectF()
    private val clipPath = Path()
    private val starMatrix = Matrix()
    private val starfield = ArrayList<StarDot>()
    private var progress = 0f
    private var starMode = false
    private var phase = 0f
    private var starAnimator: ValueAnimator? = null
    private var starFrame: Bitmap? = null
    private var starLoading = false

    var onProgressPreview: ((Int) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "竖向档位滑条"
        val random = Random(7)
        repeat(18) {
            starfield += StarDot(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.7f + random.nextFloat() * 1.6f,
                phase = random.nextFloat() * 6.28f,
                speed = 0.6f + random.nextFloat() * 1.4f
            )
        }
    }

    fun setProgress(value: Int) {
        applyProgress(value.coerceIn(0, 100) / 100f)
    }

    private fun applyProgress(value: Float) {
        progress = value
        updateStarMode()
        invalidate()
    }

    private fun updateStarMode() {
        val next = progress >= 0.995f
        if (next == starMode) return
        starMode = next
        if (starMode) {
            ensureStarFrame()
            if (MotionUtils.ambientAllowed(context)) startStarAnimator()
        } else {
            stopStarAnimator()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = dp(18f)
        val bottom = height - dp(18f)
        if (bottom <= top) return
        val centerX = width / 2f
        val barWidth = dp(50f)
        val fillWidth = (barWidth - dp(6f)).coerceAtLeast(dp(4f))
        trackRect.set(
            centerX - barWidth / 2f, top,
            centerX + barWidth / 2f, bottom
        )

        trackPaint.color = if (starMode) {
            Color.parseColor("#05060A")
        } else {
            ColorUtils.setAlphaComponent(tokens.textTertiary, 80)
        }
        canvas.drawRoundRect(trackRect, barWidth / 2f, barWidth / 2f, trackPaint)

        if (starMode) {
            drawStarfield(canvas, trackRect, barWidth)
        } else {
            val fillTop = bottom - (bottom - top) * progress
            if (fillTop < bottom - dp(1f)) {
                fillRect.set(
                    centerX - fillWidth / 2f, fillTop,
                    centerX + fillWidth / 2f, bottom
                )
                fillPaint.shader = LinearGradient(
                    0f, bottom, 0f, fillTop,
                    gradientColors(),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(fillRect, fillWidth / 2f, fillWidth / 2f, fillPaint)
                fillPaint.shader = null

                // 米白滑块，宽度略大于填充
                knobPaint.color = AgentInkColors.CREAM
                val knobHalf = fillWidth / 2f + dp(2f)
                canvas.drawRoundRect(
                    centerX - knobHalf, fillTop - dp(2f),
                    centerX + knobHalf, fillTop + dp(2f),
                    dp(2f), dp(2f), knobPaint
                )
            }
        }
    }

    private fun gradientColors(): IntArray = when {
        progress < 0.34f -> intArrayOf(
            Color.parseColor("#A8E6FF"),
            Color.parseColor("#6FB6FF")
        )
        progress < 0.67f -> intArrayOf(
            Color.parseColor("#A8E6FF"),
            Color.parseColor("#6FB6FF"),
            Color.parseColor("#8B6CFF")
        )
        else -> intArrayOf(
            Color.parseColor("#A8E6FF"),
            Color.parseColor("#6FB6FF"),
            Color.parseColor("#8B6CFF"),
            Color.parseColor("#B45CFF"),
            Color.parseColor("#E05CFF"),
            Color.parseColor("#FF6FD8")
        )
    }

    private fun drawStarfield(canvas: Canvas, rect: RectF, barWidth: Float) {
        val frame = starFrame
        clipPath.reset()
        clipPath.addRoundRect(rect, barWidth / 2f, barWidth / 2f, Path.Direction.CW)
        val layer = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(rect, trackPaint)
        if (frame != null) {
            val shader = BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(rect.width() / frame.width, rect.height() / frame.height)
            starMatrix.setScale(scale, scale)
            starMatrix.postTranslate(
                rect.centerX() - frame.width * scale / 2f,
                rect.centerY() - frame.height * scale / 2f
            )
            shader.setLocalMatrix(starMatrix)
            starPaint.shader = shader
            starPaint.alpha = 235
            canvas.drawRect(rect, starPaint)
            starPaint.shader = null
        }
        // 动态闪烁星点；没有解码到视频帧时也能呈现星空
        starfield.forEach { star ->
            val x = rect.left + rect.width() * star.x
            val y = rect.top + rect.height() * star.y
            val alpha = (90 + 150 * (sin(phase * star.speed + star.phase) * 0.5 + 0.5)).toInt()
            twinklePaint.color = ColorUtils.setAlphaComponent(
                if (star.radius > 1.7f) Color.parseColor("#9BD8FF") else Color.WHITE,
                alpha
            )
            canvas.drawCircle(x, y, star.radius * density, twinklePaint)
        }
        canvas.restoreToCount(layer)
    }

    private fun ensureStarFrame() {
        if (starFrame != null || starLoading) return
        starLoading = true
        val appContext = context.applicationContext
        Thread {
            val frame = StarrySkyFrameCache.get(appContext)
            post {
                starFrame = frame
                starLoading = false
                invalidate()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun startStarAnimator() {
        if (starAnimator?.isStarted == true) return
        starAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                phase = (it.animatedValue as Float) * 6.28f
                invalidate()
            }
            start()
        }
    }

    private fun stopStarAnimator() {
        starAnimator?.cancel()
        starAnimator = null
        phase = 0f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromY(event.y, commit = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateFromY(event.y, commit = true)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromY(y: Float, commit: Boolean) {
        val top = dp(18f)
        val bottom = height - dp(18f)
        if (bottom <= top) return
        val raw = ((bottom - y) / (bottom - top)).coerceIn(0f, 1f)
        applyProgress(raw)
        onProgressPreview?.invoke((raw * 100).toInt())
        if (commit) {
            val snapped = snap(raw)
            applyProgress(snapped)
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onProgressChanged?.invoke((snapped * 100).toInt())
        }
    }

    private fun snap(value: Float): Float {
        val stops = floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f)
        var best = stops[0]
        for (stop in stops) {
            if (abs(stop - value) < abs(best - value)) best = stop
        }
        return best
    }

    override fun onDetachedFromWindow() {
        stopStarAnimator()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density

    private data class StarDot(
        val x: Float,
        val y: Float,
        val radius: Float,
        val phase: Float,
        val speed: Float
    )
}
