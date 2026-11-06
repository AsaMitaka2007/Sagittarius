package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import kotlin.math.sin

interface AnimatableRichSpan {
    fun start()
    fun stop()
}

data class RichText(val text: SpannableString, val animators: List<AnimatableRichSpan>)

/**
 * 容错解析颜色/大小/发光/闪烁/抖动/旋转。
 * 未闭合标签会等待下一次流式更新，不会崩溃或提前应用。
 */
object RichTextParser {
    private data class OpenTag(val name: String, val value: String, val start: Int)

    private val recognized = setOf("color", "size", "glow", "blink", "spin", "shake")

    fun parse(source: String, invalidate: () -> Unit, animate: Boolean = true): RichText {
        val out = SpannableStringBuilder()
        val open = ArrayDeque<OpenTag>()
        val animators = mutableListOf<AnimatableRichSpan>()
        var i = 0
        while (i < source.length) {
            if (source[i] != '<') {
                out.append(source[i])
                i++
                continue
            }
            val end = source.indexOf('>', i)
            if (end < 0) break // 半截标签：停止渲染尾部，等下一次流式更新
            val raw = source.substring(i + 1, end).trim()
            val closing = raw.startsWith("/")
            val body = if (closing) raw.substring(1) else raw
            val eq = body.indexOf('=')
            val name = (if (eq >= 0) body.substring(0, eq) else body).trim().lowercase()
            val value = if (eq >= 0) body.substring(eq + 1).trim().lowercase() else ""
            if (name !in recognized) {
                out.append(source, i, end + 1)
                i = end + 1
                continue
            }
            if (!closing) {
                open.addLast(OpenTag(name, value, out.length))
                i = end + 1
                continue
            }
            val index = open.indexOfLast { it.name == name }
            if (index >= 0) {
                val tag = open.removeAt(index)
                if (out.length > tag.start) {
                    applySpan(out, tag, tag.start, out.length, open, invalidate, animators, animate)
                }
            }
            i = end + 1
        }
        return RichText(SpannableString(out), animators)
    }

    private fun applySpan(
        out: SpannableStringBuilder,
        tag: OpenTag,
        start: Int,
        end: Int,
        remainingOpen: ArrayDeque<OpenTag>,
        invalidate: () -> Unit,
        animators: MutableList<AnimatableRichSpan>,
        animate: Boolean
    ) {
        val color = nearestColor(remainingOpen, start)
        when (tag.name) {
            "color" -> when (tag.value) {
                "rainbow" -> {
                    val span = RainbowSpan(invalidate)
                    if (animate) animators.add(span)
                    out.setSpan(span, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> out.setSpan(
                    ForegroundColorSpan(colorFor(tag.value)),
                    start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            "size" -> out.setSpan(
                RelativeSizeSpan(if (tag.value == "small") 0.8f else 1.3f),
                start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            "glow" -> {
                val span = GlowSpan(invalidate)
                if (animate) animators.add(span)
                out.setSpan(span, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "blink" -> {
                val span = BlinkSpan(invalidate)
                if (animate) animators.add(span)
                out.setSpan(span, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "shake" -> {
                val span = ShakeSpan(invalidate, color)
                if (animate) animators.add(span)
                out.setSpan(span, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            "spin" -> {
                val span = SpinSpan(invalidate, color)
                if (animate) animators.add(span)
                out.setSpan(span, start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun nearestColor(open: ArrayDeque<OpenTag>, start: Int): Int? {
        val colorTag = open.lastOrNull { it.name == "color" && it.start <= start } ?: return null
        return if (colorTag.value == "rainbow") null else colorFor(colorTag.value)
    }

    private fun colorFor(value: String): Int = when (value) {
        "red" -> Color.parseColor("#FF5252")
        "orange" -> Color.parseColor("#FF9800")
        "yellow" -> Color.parseColor("#FFD600")
        "green" -> Color.parseColor("#4CAF50")
        "cyan" -> Color.parseColor("#00BCD4")
        "blue" -> Color.parseColor("#448AFF")
        "purple" -> Color.parseColor("#AB47BC")
        "gold" -> Color.parseColor("#FFD700")
        else -> Color.parseColor("#FFB74D")
    }
}

class RainbowSpan(private val invalidate: () -> Unit) : CharacterStyle(), AnimatableRichSpan {
    private var animator: ValueAnimator? = null
    private var phase = 0f
    private var shader: LinearGradient? = null
    private val matrix = Matrix()
    private val colors = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#FF8A00"),
        Color.parseColor("#FFD60A"), Color.parseColor("#34C759"),
        Color.parseColor("#00E5FF"), Color.parseColor("#4DA8FF"),
        Color.parseColor("#A86BFF")
    )

    override fun updateDrawState(tp: TextPaint) {
        if (shader == null) shader = LinearGradient(0f, 0f, 460f, 0f, colors, null, Shader.TileMode.CLAMP)
        matrix.setTranslate(phase * 460f, 0f)
        shader?.setLocalMatrix(matrix)
        tp.shader = shader
    }

    override fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200L; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun stop() { animator?.cancel(); animator = null }
}

class GlowSpan(private val invalidate: () -> Unit) : CharacterStyle(), AnimatableRichSpan {
    private var animator: ValueAnimator? = null
    private var radius = 4f
    private val glowColor = Color.parseColor("#FFD700")

    override fun updateDrawState(tp: TextPaint) {
        tp.setShadowLayer(radius, 0f, 0f, glowColor)
    }

    override fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(2f, 8f).apply {
            duration = 1600L; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { radius = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun stop() { animator?.cancel(); animator = null }
}

class BlinkSpan(private val invalidate: () -> Unit) : CharacterStyle(), AnimatableRichSpan {
    private var animator: ValueAnimator? = null
    private var alpha = 1f

    override fun updateDrawState(tp: TextPaint) {
        // 呼吸灯：0.2 到 1.0 平滑循环，而不是硬开关。
        tp.alpha = (alpha.coerceIn(0.2f, 1f) * 255f).toInt()
    }

    override fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0.2f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { alpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun stop() { animator?.cancel(); animator = null }
}

class ShakeSpan(
    private val invalidate: () -> Unit,
    private val color: Int? = null
) : ReplacementSpan(), AnimatableRichSpan {
    private var animator: ValueAnimator? = null
    private var phase = 0f

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int = paint.measureText(text, start, end).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val content = text ?: return
        color?.let { paint.color = it }
        val offset = sin(phase * Math.PI * 2).toFloat() * paint.textSize * 0.035f
        canvas.save()
        canvas.translate(offset, 0f)
        canvas.drawText(content, start, end, x, y.toFloat(), paint)
        canvas.restore()
    }

    override fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun stop() { animator?.cancel(); animator = null }
}

class SpinSpan(
    private val invalidate: () -> Unit,
    private val color: Int? = null
) : ReplacementSpan(), AnimatableRichSpan {
    private var animator: ValueAnimator? = null
    private var phase = 0f

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int = paint.measureText(text, start, end).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val content = text ?: return
        color?.let { paint.color = it }
        var cursor = x
        val centerY = y - (paint.ascent() + paint.descent()) / 2f
        for (index in start until end) {
            val ch = content.subSequence(index, index + 1)
            val width = paint.measureText(ch, 0, 1)
            val cx = cursor + width / 2f
            canvas.save()
            canvas.rotate(phase * 360f + (index - start) * 24f, cx, centerY)
            canvas.drawText(ch, 0, 1, cursor, y.toFloat(), paint)
            canvas.restore()
            cursor += width
        }
    }

    override fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun stop() { animator?.cancel(); animator = null }
}
