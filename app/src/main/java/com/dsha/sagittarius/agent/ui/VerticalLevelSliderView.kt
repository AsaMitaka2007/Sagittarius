package com.dsha.sagittarius.agent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.ButtonAccentManager
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 竖向 4 档滑条：为推理强度 / 生动度胶囊提供与旧横向滑条一致的视觉语言。
 */
class VerticalLevelSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val accent = ButtonAccentManager.current
    private val accentColor: Int
        get() = if (accent.isTransparent) tokens.textPrimary else accent.startColor
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f

    var onProgressPreview: ((Int) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "竖向档位滑条"
    }

    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 100) / 100f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val top = 18f * density
        val bottom = height - 18f * density
        if (bottom <= top) return
        val trackWidth = 6f * density
        val knobY = bottom - (bottom - top) * progress

        trackPaint.strokeWidth = trackWidth
        trackPaint.strokeCap = Paint.Cap.ROUND
        trackPaint.color = ColorUtils.setAlphaComponent(tokens.textTertiary, 55)
        canvas.drawLine(cx, top, cx, bottom, trackPaint)

        fillPaint.strokeWidth = trackWidth
        fillPaint.strokeCap = Paint.Cap.ROUND
        fillPaint.color = accentColor
        canvas.drawLine(cx, knobY, cx, bottom, fillPaint)

        // 4 个档位刻度
        tickPaint.strokeWidth = 1.4f * density
        listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { fraction ->
            val y = bottom - (bottom - top) * fraction
            tickPaint.color = if (fraction <= progress + 0.01f) {
                ColorUtils.setAlphaComponent(accentColor, 180)
            } else {
                ColorUtils.setAlphaComponent(tokens.textTertiary, 70)
            }
            canvas.drawLine(cx - 5f * density, y, cx + 5f * density, y, tickPaint)
        }

        // 光晕 + 旋钮
        glowPaint.color = ColorUtils.setAlphaComponent(accentColor, 45)
        canvas.drawCircle(cx, knobY, 15f * density, glowPaint)
        glowPaint.color = ColorUtils.setAlphaComponent(accentColor, 24)
        canvas.drawCircle(cx, knobY, 22f * density, glowPaint)
        knobPaint.color = accentColor
        canvas.drawCircle(cx, knobY, 7f * density, knobPaint)
        knobPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, knobY, 2.6f * density, knobPaint)
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
        val top = 18f * density
        val bottom = height - 18f * density
        if (bottom <= top) return
        val raw = ((bottom - y) / (bottom - top)).coerceIn(0f, 1f)
        progress = raw
        invalidate()
        onProgressPreview?.invoke((progress * 100).roundToInt())
        if (commit) {
            val snapped = snap(progress)
            progress = snapped
            invalidate()
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onProgressChanged?.invoke((progress * 100).roundToInt())
        }
    }

    private fun snap(value: Float): Float {
        val stops = floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f)
        return stops.minByOrNull { abs(it - value) } ?: value
    }
}
