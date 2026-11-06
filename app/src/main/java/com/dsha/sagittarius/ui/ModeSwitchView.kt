package com.dsha.sagittarius.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** “记账 / 饮食”模式切换胶囊。 */
class ModeSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val tokens = ThemeTokens.of(context)
    private val account = chip("记账", false)
    private val diet = chip("饮食", true)

    var onModeSelected: ((AppMode) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(4f), dp(3f), dp(4f), dp(3f))
        background = GradientDrawable().apply {
            cornerRadius = dp(18f).toFloat()
            setColor(tokens.iconCircle)
            setStroke(dp(1f), tokens.divider)
        }
        addView(account, LayoutParams(0, dp(30f), 1f))
        addView(diet, LayoutParams(0, dp(30f), 1f))
        account.setOnClickListener { onModeSelected?.invoke(AppMode.ACCOUNT) }
        diet.setOnClickListener { onModeSelected?.invoke(AppMode.DIET) }
    }

    fun setMode(mode: AppMode) {
        apply(account, mode == AppMode.ACCOUNT)
        apply(diet, mode == AppMode.DIET)
    }

    private fun apply(view: TextView, selected: Boolean) {
        val accent = ButtonAccentManager.current
        view.background = if (selected) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                accent.colors
            ).apply { cornerRadius = dp(15f).toFloat() }
        } else {
            null
        }
        view.setTextColor(ColorStateList.valueOf(if (selected) accent.textColor else tokens.textSecondary))
    }

    private fun chip(textValue: String, selected: Boolean): TextView = TextView(context).apply {
        text = textValue
        gravity = Gravity.CENTER
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isClickable = true
        isFocusable = true
        val accent = ButtonAccentManager.current
        setTextColor(ColorStateList.valueOf(if (selected) accent.textColor else tokens.textSecondary))
        background = if (selected) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                accent.colors
            ).apply { cornerRadius = dp(15f).toFloat() }
        } else {
            null
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
