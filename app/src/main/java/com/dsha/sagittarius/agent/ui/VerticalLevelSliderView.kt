package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.MotionUtils
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.abs
import kotlin.math.cos
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
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val fillRect = RectF()
    private val clipPath = Path()
    private val sparklePath = Path()
    private val starMatrix = Matrix()
    private val starfield = ArrayList<StarDot>()
    private var progress = 0f
    private var starMode = false
    private var phase = 0f
    private var starAnimator: ValueAnimator? = null
    private var snapAnimator: ValueAnimator? = null
    private var starFrame: Bitmap? = null
    private var starLoading = false

    var onProgressPreview: ((Int) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "竖向档位滑条"
        // 四角星芒：中心到四角，腰部收窄
        sparklePath.moveTo(0f, -1f)
        sparklePath.quadTo(0.18f, -0.18f, 1f, 0f)
        sparklePath.quadTo(0.18f, 0.18f, 0f, 1f)
        sparklePath.quadTo(-0.18f, 0.18f, -1f, 0f)
        sparklePath.quadTo(-0.18f, -0.18f, 0f, -1f)
        sparklePath.close()
        val random = Random(7)
        repeat(26) {
            starfield += StarDot(
                x = 0.06f + random.nextFloat() * 0.88f,
                y = 0.05f + random.nextFloat() * 0.90f,
                size = 1.1f + random.nextFloat() * 1.8f,
                phase = random.nextFloat() * 6.28f,
                twinkleSpeed = 0.55f + random.nextFloat() * 0.85f,
                driftSpeedX = 0.35f + random.nextFloat() * 0.45f,
                driftSpeedY = 0.28f + random.nextFloat() * 0.38f,
                driftX = 0.015f + random.nextFloat() * 0.035f,
                driftY = 0.02f + random.nextFloat() * 0.05f,
                rotationSpeed = 0.32f + random.nextFloat() * 0.55f
            )
        }
    }

    fun setProgress(value: Int) {
        snapAnimator?.cancel()
        applyProgress(value.coerceIn(0, 100) / 100f)
    }

    /** 关闭胶囊时调用：把当前预览吸附到档位并立即提交最终配置。 */
    fun commit() {
        snapAnimator?.cancel()
        val target = snap(progress)
        applyProgress(target)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onProgressChanged?.invoke((target * 100).toInt())
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

    /**
     * 用连续色相插值生成填充色：浅蓝(约196°) → 蓝紫(约260°) → 紫(约300°)
     * → 品红(约326°)。不使用分档阈值，避免拖动到临界点时颜色突然跳变。
     */
    private fun gradientColors(): IntArray {
        val p = progress.coerceIn(0f, 1f)
        val hue = 196f + 130f * p
        val bottom = Color.HSVToColor(floatArrayOf(hue, 0.24f, 1f))
        val middle = Color.HSVToColor(floatArrayOf(hue + 8f, 0.52f, 1f))
        val top = Color.HSVToColor(floatArrayOf(hue + 16f, 0.68f, 1f))
        return intArrayOf(bottom, middle, top)
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
            starPaint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )
            starPaint.alpha = 235
            canvas.drawRect(rect, starPaint)
            starPaint.shader = null
            starPaint.colorFilter = null
        }
        // 十字星芒粒子：缓慢平移、旋转、呼吸淡入淡出，并带白色荧光
        val t = phase
        starfield.forEach { star ->
            val x = rect.left + rect.width() * (
                star.x + sin(t * star.driftSpeedX + star.phase) * star.driftX
            )
            val y = rect.top + rect.height() * (
                star.y + cos(t * star.driftSpeedY + star.phase * 1.7f) * star.driftY
            )
            val wave = sin(t * star.twinkleSpeed + star.phase) * 0.5f + 0.5f
            val intensity = 0.12f + 0.88f * wave * wave
            val alpha = (255 * intensity).toInt().coerceIn(0, 255)
            val radius = star.size * density
            val rotation = (t * star.rotationSpeed + star.phase) * 57.2958f
            // 荧光：两层低透明度光晕 + 放大星芒
            twinklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, alpha / 9)
            canvas.drawCircle(x, y, radius * 9f, twinklePaint)
            twinklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, alpha / 5)
            canvas.drawCircle(x, y, radius * 5.4f, twinklePaint)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)
            canvas.scale(radius * 2.7f, radius * 2.7f)
            sparklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, alpha / 6)
            canvas.drawPath(sparklePath, sparklePaint)
            canvas.restore()

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rotation)
            canvas.scale(radius, radius)
            sparklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, alpha)
            canvas.drawPath(sparklePath, sparklePaint)
            // 中心亮核，增强“星芒”质感
            sparklePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (alpha * 0.85f).toInt())
            twinklePaint.color = sparklePaint.color
            canvas.drawCircle(0f, 0f, 0.34f, twinklePaint)
            canvas.restore()
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
            duration = 9000L
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
            animateSnap(snap(raw))
        }
    }

    private fun animateSnap(target: Float) {
        snapAnimator?.cancel()
        if (abs(target - progress) < 0.001f) {
            applyProgress(target)
            onProgressChanged?.invoke((target * 100).toInt())
            return
        }
        snapAnimator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 150L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                applyProgress(it.animatedValue as Float)
                onProgressPreview?.invoke((progress * 100).toInt())
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    applyProgress(target)
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onProgressChanged?.invoke((target * 100).toInt())
                }
            })
            start()
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
        snapAnimator?.cancel()
        snapAnimator = null
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density

    private data class StarDot(
        val x: Float,
        val y: Float,
        val size: Float,
        val phase: Float,
        val twinkleSpeed: Float,
        val driftSpeedX: Float,
        val driftSpeedY: Float,
        val driftX: Float,
        val driftY: Float,
        val rotationSpeed: Float
    )
}
