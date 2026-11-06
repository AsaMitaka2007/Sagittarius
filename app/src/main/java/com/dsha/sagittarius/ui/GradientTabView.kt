package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.dsha.sagittarius.R

/**
 * 底部渐变 Tab：选中时图标与文字都用青绿→蓝紫→淡粉 Shader；
 * 未选中时使用低透明度中性色。按压缩放克制。
 */
class GradientTabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val tokens = ThemeTokens.of(context)
    private var label: String = ""
    private var icon: Drawable? = null
    private val accent = ButtonAccentManager.current
    private val shaderColors = intArrayOf(
        accent.startColor,
        accent.midColor,
        accent.endColor,
        accent.startColor
    )

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconRect = Rect()
    private val indicatorRect = RectF()
    private val xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.GradientTabView, defStyleAttr, 0)
        label = a.getString(R.styleable.GradientTabView_tabLabel).orEmpty()
        val iconRes = a.getResourceId(R.styleable.GradientTabView_tabIcon, 0)
        a.recycle()
        if (iconRes != 0) {
            icon = AppCompatResources.getDrawable(context, iconRes)?.mutate()
        }
        isClickable = true
        isFocusable = true
        contentDescription = label
        isSelected = false
        alpha = 0.78f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(86f)
        val desiredHeight = dp(66f)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        alpha = if (selected) 1f else 0.78f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        val selected = isSelected
        val shader = if (selected) {
            LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                shaderColors,
                null,
                Shader.TileMode.REPEAT
            )
        } else {
            null
        }

        val iconSize = dp(22f)
        val gap = dp(5f)
        val textHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
        // 6 个 Tab 时未选中项只显示图标，避免底部标签挤压；选中项保留文字。
        val contentHeight = iconSize + (if (selected) gap + textHeight else 0f)
        val top = (height - contentHeight) / 2f - dp(1f)
        val cx = width / 2f
        val iconLeft = (cx - iconSize / 2f).toInt()
        val iconTop = top.toInt()
        iconRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)

        icon?.let { drawable ->
            if (selected && shader != null) {
                val layer = canvas.saveLayer(
                    iconRect.left.toFloat(), iconRect.top.toFloat(),
                    iconRect.right.toFloat(), iconRect.bottom.toFloat(),
                    null
                )
                drawable.setTint(Color.WHITE)
                drawable.bounds = iconRect
                drawable.draw(canvas)
                shaderPaint.shader = shader
                shaderPaint.xfermode = xfermode
                canvas.drawRect(iconRect, shaderPaint)
                shaderPaint.xfermode = null
                canvas.restoreToCount(layer)
            } else {
                drawable.setTint(tokens.tabInactive)
                drawable.bounds = iconRect
                drawable.draw(canvas)
            }
        }

        if (selected) {
            val baseline = top + iconSize + gap - textPaint.fontMetrics.ascent
            if (shader != null && !accent.isLight) {
                textPaint.shader = shader
            } else {
                textPaint.shader = null
                textPaint.color = if (shader != null) accent.textColor else tokens.tabInactive
            }
            canvas.drawText(label, cx, baseline, textPaint)
        }

        if (selected && shader != null) {
            indicatorPaint.shader = shader
            indicatorRect.set(cx - dp(10f), dp(4f).toFloat(), cx + dp(10f), dp(6f).toFloat())
            canvas.drawRoundRect(indicatorRect, dp(2f).toFloat(), dp(2f).toFloat(), indicatorPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> MotionUtils.press(this)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> MotionUtils.release(this)
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        MotionUtils.cancelPress(this)
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Int = (value * density).toInt()
}
