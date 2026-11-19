package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.R

/**
 * 首页 AI 入口：深色星图底 + 圆角裁切的星座 logo。
 *
 * 不沿用 FAB 的旋转按压，改为缩放 + 外发光，避免矩形按钮出现怪异旋转。
 */
class AgentEntryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val accent = ButtonAccentManager.current
    private val logo = BitmapFactory.decodeResource(resources, R.drawable.agent_entry_logo)
    private val rect = RectF()
    private val inset = 6f * density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private var glow = 0f
    private var pressed = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.agent_fab_description)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(inset, inset, w - inset, h - inset)
        backgroundPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(0xF20B1220.toInt(), 0xF21B2742.toInt(), 0xF20B1220.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        borderPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(accent.startColor, accent.endColor),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 18f * density
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        val target = minOf(rect.width(), rect.height()) * 0.74f
        val left = rect.centerX() - target / 2f
        val top = rect.centerY() - target / 2f
        logoPaint.alpha = (255 * (0.94f - glow * 0.06f)).toInt()
        canvas.drawBitmap(logo, null, RectF(left, top, left + target, top + target), logoPaint)

        // 外圈描边 + 按压光晕；光晕限制在 view 内留白中，避免被父布局裁掉
        val inner = RectF(rect).apply { inset(0.6f, 0.6f) }
        canvas.drawRoundRect(inner, radius, radius, borderPaint)
        if (glow > 0f) {
            glowPaint.color = ColorUtils.setAlphaComponent(accent.startColor, (150 * glow).toInt())
            val spread = (1f + glow * 3.8f)
            val outer = RectF(rect).apply { inset(-spread, -spread) }
            canvas.drawRoundRect(outer, radius + spread, radius + spread, glowPaint)
            glowPaint.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), (70 * glow).toInt())
            canvas.drawRoundRect(inner, radius, radius, glowPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                pressed = true
                animateGlow(1f)
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!pressed) return false
                pressed = false
                isPressed = false
                animateGlow(0.35f)
                animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                isPressed = false
                animateGlow(0f)
                animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun animateGlow(target: Float) {
        val start = glow
        valueAnimator?.cancel()
        valueAnimator = android.animation.ValueAnimator.ofFloat(start, target).apply {
            duration = 180L
            addUpdateListener {
                glow = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private var valueAnimator: android.animation.ValueAnimator? = null
}
