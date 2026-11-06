package com.dsha.sagittarius.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.view.View
import kotlin.math.abs

/** 左侧内容 + 右侧红色删除按钮；横向滑动超过一半吸附展开。 */
class SwipeRevealLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startTranslation = 0f
    private var dragging = false
    private var revealed = false
    private var swipeEnabled = true
    private var deleteWidthPx = 0
    private var onRevealChanged: ((SwipeRevealLayout, Boolean) -> Unit)? = null

    fun setDeleteWidth(width: Int) {
        deleteWidthPx = width
        requestLayout()
    }

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
        if (!enabled) close()
    }

    fun setOnRevealChanged(listener: (SwipeRevealLayout, Boolean) -> Unit) {
        onRevealChanged = listener
    }

    fun isRevealed(): Boolean = revealed

    fun close(animate: Boolean = true) {
        val content = getChildAt(0) ?: return
        if (animate) {
            content.animate().translationX(0f).setDuration(160L).start()
        } else {
            content.translationX = 0f
        }
        revealed = false
        onRevealChanged?.invoke(this, false)
    }

    fun open() {
        val content = getChildAt(0) ?: return
        content.animate().translationX(-deleteWidthPx.toFloat()).setDuration(160L).start()
        revealed = true
        onRevealChanged?.invoke(this, true)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (deleteWidthPx <= 0) deleteWidthPx = (80 * resources.displayMetrics.density).toInt()
        getChildAt(1)?.measure(
            MeasureSpec.makeMeasureSpec(deleteWidthPx, MeasureSpec.EXACTLY),
            heightMeasureSpec
        )
        getChildAt(0)?.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            heightMeasureSpec
        )
        setMeasuredDimension(width, getChildAt(0)?.measuredHeight ?: 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        getChildAt(0)?.layout(0, 0, w, h)
        getChildAt(1)?.layout(w - deleteWidthPx, 0, w, h)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                startTranslation = getChildAt(0)?.translationX ?: 0f
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        val content = getChildAt(0) ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                startTranslation = content.translationX
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    val target = (startTranslation + dx).coerceIn(-deleteWidthPx.toFloat(), 0f)
                    content.translationX = target
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging) {
                    // 点击已展开的内容区：收回，不触发其他操作。
                    if (revealed) close()
                    return true
                }
                val half = deleteWidthPx / 2f
                if (-content.translationX >= half) open() else close()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
