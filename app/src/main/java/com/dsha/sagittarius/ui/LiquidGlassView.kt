package com.dsha.sagittarius.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.util.BudgetPalette
import com.dsha.sagittarius.util.GlassPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 液态玻璃胶囊背景。
 *
 * 层次：外发光 → 半透明深色底 → 两团流动液态渐变 → 顶部高光 → 内阴影 →
 * 1px 渐变描边 → 低透明噪点。Android 12+ 不对整层使用 RenderEffect，
 * 因为系统悬浮窗无法读取背后桌面像素，强行模糊只会把文字一起糊掉；
 * 这里用多层渐变 + 噪点做跨版本一致、低耗电的玻璃降级实现。
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onPaletteChanged: ((GlassPalette) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val rect = RectF()
    private val glowRect = RectF()
    private val innerRect = RectF()
    private val innerRect2 = RectF()
    private val noiseBitmap: Bitmap = createNoiseBitmap()
    private val noiseShader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private var baseGradient: LinearGradient? = null
    private var highlightGradient: LinearGradient? = null
    private var cachedWidth = 0
    private var cachedHeight = 0

    private var animatedRatio: Double? = null
    private var targetRatio: Double? = null
    private var ratioAnimator: ValueAnimator? = null
    private var phase = 0f
    private var isExpanded = false
    private var compact = false
    private var palette: GlassPalette = BudgetPalette.forRatio(null, ThemeManager.isLight)

    private val phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 13_000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        noisePaint.shader = noiseShader
        noisePaint.alpha = 18
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setRemainingRatio(ratio: Double?, animate: Boolean = true) {
        val normalized = ratio
        if (targetRatio == normalized && animatedRatio != null) {
            notifyPalette()
            return
        }
        targetRatio = normalized
        if (!animate || animatedRatio == null) {
            animatedRatio = normalized ?: 1.0
            palette = BudgetPalette.forRatio(normalized, ThemeManager.isLight)
            notifyPalette()
            invalidate()
            return
        }
        ratioAnimator?.cancel()
        val from = animatedRatio ?: 1.0
        val to = normalized ?: 1.0
        ratioAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                animatedRatio = from + (to - from) * t
                palette = BudgetPalette.forRatio(animatedRatio, ThemeManager.isLight)
                notifyPalette()
                invalidate()
            }
        }
        ratioAnimator?.start()
    }

    fun setExpanded(expanded: Boolean) {
        isExpanded = expanded
        invalidate()
    }

    fun setCompact(value: Boolean) {
        if (compact == value) return
        compact = value
        if (compact) {
            if (phaseAnimator.isStarted) phaseAnimator.pause()
        } else if (isAttachedToWindow && isShown) {
            if (phaseAnimator.isPaused) phaseAnimator.resume()
            else if (!phaseAnimator.isStarted) phaseAnimator.start()
        }
        invalidate()
    }

    private fun notifyPalette() {
        onPaletteChanged?.invoke(palette)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!compact && !phaseAnimator.isStarted) phaseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        phaseAnimator.cancel()
        ratioAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 7f * density
        val radius = if (isExpanded) 28f * density else height / 2f
        rect.set(inset, inset, width - inset, height - inset)
        if (rect.width() <= 0f || rect.height() <= 0f) return

        ensureCachedGradients(rect)
        drawOuterGlow(canvas, rect, radius)
        drawGlassBase(canvas, rect, radius)
        drawLiquidBlobs(canvas, rect, radius)
        drawTopHighlight(canvas, rect, radius)
        drawInnerShadow(canvas, rect, radius)
        drawBorder(canvas, rect, radius)
        drawNoise(canvas, rect, radius)
    }

    private fun ensureCachedGradients(r: RectF) {
        if (cachedWidth == width && cachedHeight == height && baseGradient != null) return
        cachedWidth = width
        cachedHeight = height
        baseGradient = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(tokens.cardFillTop, tokens.cardFillBottom, tokens.cardFillBottom),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        highlightGradient = LinearGradient(
            r.left, r.top, r.left, r.top + r.height() * 0.52f,
            intArrayOf(
                ColorUtils.setAlphaComponent(tokens.cardHighlight, 44),
                ColorUtils.setAlphaComponent(tokens.cardHighlight, 7),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.34f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawOuterGlow(canvas: Canvas, r: RectF, radius: Float) {
        for (i in 10 downTo 1) {
            val grow = i * 1.5f * density
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = (2.2f * density)
            glowPaint.shader = null
            glowPaint.color = ColorUtils.setAlphaComponent(
                palette.primary,
                (2 + (10 - i) * 1.5f).toInt().coerceIn(2, 20)
            )
            glowRect.set(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow)
            canvas.drawRoundRect(glowRect, radius + grow, radius + grow, glowPaint)
        }
    }

    private fun drawGlassBase(canvas: Canvas, r: RectF, radius: Float) {
        fillPaint.shader = baseGradient
        canvas.drawRoundRect(r, radius, radius, fillPaint)
    }

    private fun drawLiquidBlobs(canvas: Canvas, r: RectF, radius: Float) {
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(r, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val angle = phase * Math.PI * 2.0
        val c1x = r.left + r.width() * (0.20f + 0.12f * cos(angle).toFloat())
        val c1y = r.top + r.height() * (0.18f + 0.30f * sin(angle * 0.8).toFloat())
        val c2x = r.left + r.width() * (0.82f + 0.10f * sin(angle * 0.7).toFloat())
        val c2y = r.top + r.height() * (0.76f + 0.18f * cos(angle * 0.9).toFloat())
        val radius1 = r.width() * 0.58f

        liquidPaint.shader = RadialGradient(
            c1x, c1y, radius1,
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.primary, 96),
                ColorUtils.setAlphaComponent(palette.secondary, 34),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(c1x, c1y, radius1, liquidPaint)

        liquidPaint.shader = RadialGradient(
            c2x, c2y, r.width() * 0.52f,
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.secondary, 84),
                ColorUtils.setAlphaComponent(palette.primary, 22),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(c2x, c2y, r.width() * 0.52f, liquidPaint)
        canvas.restore()
    }

    private fun drawTopHighlight(canvas: Canvas, r: RectF, radius: Float) {
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(r, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        highlightPaint.shader = highlightGradient
        canvas.drawRoundRect(r, radius, radius, highlightPaint)
        canvas.restore()
    }

    private fun drawInnerShadow(canvas: Canvas, r: RectF, radius: Float) {
        borderPaint.shader = null
        borderPaint.strokeWidth = 2f * density
        borderPaint.color = tokens.cardShadow
        innerRect.set(
            r.left + 1.5f * density,
            r.top + 1.5f * density,
            r.right - 1.5f * density,
            r.bottom - 1.5f * density
        )
        canvas.drawRoundRect(innerRect, radius, radius, borderPaint)

        borderPaint.strokeWidth = 1f
        borderPaint.color = ColorUtils.setAlphaComponent(tokens.cardShadow, 70)
        innerRect2.set(
            r.left + 4f * density,
            r.top + 4f * density,
            r.right - 4f * density,
            r.bottom - 4f * density
        )
        canvas.drawRoundRect(
            innerRect2,
            radius - 3f * density,
            radius - 3f * density,
            borderPaint
        )
    }

    private fun drawBorder(canvas: Canvas, r: RectF, radius: Float) {
        borderPaint.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(
                ColorUtils.setAlphaComponent(tokens.cardStrokeStart, 130),
                ColorUtils.setAlphaComponent(palette.primary, 120),
                ColorUtils.setAlphaComponent(palette.secondary, 94),
                ColorUtils.setAlphaComponent(tokens.textPrimary, 34)
            ),
            floatArrayOf(0f, 0.36f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )
        borderPaint.strokeWidth = 1f
        borderPaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(r, radius, radius, borderPaint)
        borderPaint.shader = null
    }

    private fun drawNoise(canvas: Canvas, r: RectF, radius: Float) {
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(r, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        noisePaint.shader = noiseShader
        canvas.drawRect(r, noisePaint)
        canvas.restore()
    }

    private fun createNoiseBitmap(): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = Random(42)
        for (i in pixels.indices) {
            val v = random.nextInt(0, 256)
            pixels[i] = Color.argb(if (v > 128) 24 else 14, v, v, v)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    @Suppress("unused")
    private fun applyOptionalRenderEffectIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 这里不启用整层 RenderEffect：系统悬浮窗拿不到桌面背景，
            // 整层模糊会牺牲文字清晰度。保留入口便于未来在 Activity 内做真实背景模糊。
        }
    }
}
