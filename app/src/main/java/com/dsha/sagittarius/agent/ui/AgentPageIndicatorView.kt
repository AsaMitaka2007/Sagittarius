package com.dsha.sagittarius.agent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.ui.ButtonAccentManager
import com.dsha.sagittarius.ui.ThemeTokens

/** 三页滑动指示器：纯展示，不接收点击。 */
class AgentPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private val accent = ButtonAccentManager.current
    private val accentColor: Int
        get() = if (accent.isTransparent) tokens.textSecondary else accent.startColor
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var position = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setCurrent(index: Int, animate: Boolean = true) {
        val target = index.coerceIn(0, 2).toFloat()
        animator?.cancel()
        if (!animate) {
            position = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(position, target).apply {
            duration = 260L
            addUpdateListener {
                position = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setScrollProgress(value: Float) {
        animator?.cancel()
        position = value.coerceIn(0f, 2f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val spacing = 16f * density
        for (i in 0 until 3) {
            val x = cx + (i - 1) * spacing
            val distance = kotlin.math.abs(position - i).coerceAtMost(1f)
            val selected = 1f - distance
            val radius = (2.8f + selected * 0.9f) * density
            paint.color = ColorUtils.setAlphaComponent(
                if (selected > 0.5f) accentColor else tokens.textTertiary,
                (90 + selected * 165).toInt()
            )
            canvas.drawCircle(x, cy, radius, paint)
        }
        // 当前页用一段短胶囊连接，避免三个点过于分散
        val progress = position
        val x = cx + (progress - 1) * spacing
        paint.color = ColorUtils.setAlphaComponent(accentColor, 210)
        canvas.drawRoundRect(
            x - 7f * density, cy - 2.2f * density,
            x + 7f * density, cy + 2.2f * density,
            2.2f * density, 2.2f * density, paint
        )
    }
}
