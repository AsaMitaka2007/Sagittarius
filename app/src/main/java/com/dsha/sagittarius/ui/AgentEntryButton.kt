package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.dsha.sagittarius.R

/**
 * 首页 AI 入口：黑色墨水圆角底 + 星图 logo。
 * 不画流光/渐变描边，按压只做轻微缩放。
 */
class AgentEntryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val logo = BitmapFactory.decodeResource(resources, R.drawable.agent_entry_logo)
    private val rect = RectF()
    private val inset = 6f * density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE60B1220.toInt()
    }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.agent_fab_description)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 18f * density
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        val target = minOf(rect.width(), rect.height()) * 0.74f
        val left = rect.centerX() - target / 2f
        val top = rect.centerY() - target / 2f
        canvas.drawBitmap(logo, null, RectF(left, top, left + target, top + target), logoPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(170L).start()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
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
}
