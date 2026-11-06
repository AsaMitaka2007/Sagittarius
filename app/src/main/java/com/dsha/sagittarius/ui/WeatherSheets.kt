package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.weather.WeatherCities
import com.dsha.sagittarius.data.weather.WeatherCity
import com.dsha.sagittarius.data.weather.WeatherType
import com.google.android.material.bottomsheet.BottomSheetDialog

object WeatherCityPickerSheet {

    fun show(context: Context, selectedId: String, onSelected: (WeatherCity) -> Unit) {
        val sheet = BottomSheetDialog(context)
        val tokens = ThemeTokens.of(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20f), dp(context, 14f), dp(context, 20f), dp(context, 20f))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.weather_city_picker_title)
            setTextColor(tokens.textPrimary)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val search = EditText(context).apply {
            hint = context.getString(R.string.weather_city_search_hint)
            setSingleLine(true)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            textSize = 14f
            setPadding(dp(context, 12f), 0, dp(context, 12f), 0)
            background = GradientBackground.create(context, tokens)
        }
        root.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 46f)
        ).apply { topMargin = dp(context, 12f) })
        val scroll = ScrollView(context)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 360f)
        ).apply { topMargin = dp(context, 8f) })
        sheet.setContentView(root)

        fun rebuild(query: String) {
            list.removeAllViews()
            val normalized = query.trim().lowercase()
            WeatherCities.all
                .filter { normalized.isEmpty() || it.name.lowercase().contains(normalized) }
                .forEach { city ->
                    val row = TextView(context).apply {
                        text = if (city.id == selectedId) "✓  ${city.name}" else city.name
                        textSize = 15f
                        gravity = Gravity.CENTER_VERTICAL
                        setTextColor(if (city.id == selectedId) tokens.accent else tokens.textPrimary)
                        setPadding(dp(context, 8f), 0, dp(context, 8f), 0)
                        isClickable = true
                        setOnClickListener {
                            sheet.dismiss()
                            onSelected(city)
                        }
                    }
                    list.addView(row, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 48f)
                    ))
                }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                rebuild(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        rebuild("")
        sheet.show()
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private object GradientBackground {
        fun create(context: Context, tokens: ThemeTokens) = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 12f).toFloat()
            setColor(tokens.iconCircle)
            setStroke(dp(context, 1f), tokens.divider)
        }

        private fun dp(context: Context, value: Float): Int =
            (value * context.resources.displayMetrics.density).toInt()
    }
}

object WeatherDebugSheet {

    fun show(context: Context, current: WeatherType?, onSelected: (WeatherType?) -> Unit) {
        val sheet = BottomSheetDialog(context)
        val tokens = ThemeTokens.of(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20f), dp(context, 14f), dp(context, 20f), dp(context, 22f))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.weather_debug_title)
            setTextColor(tokens.textPrimary)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(context).apply {
            text = "调试覆盖优先级最高，会忽略定位与 API；仅用于测试。"
            setTextColor(tokens.textTertiary)
            textSize = 11f
            setPadding(0, dp(context, 6f), 0, dp(context, 8f))
        })
        val options = listOf(
            WeatherType.SUNNY to R.string.weather_debug_sunny,
            WeatherType.RAINY to R.string.weather_debug_rainy,
            WeatherType.SNOWY to R.string.weather_debug_snowy,
            WeatherType.CLOUDY to R.string.weather_debug_cloudy
        )
        options.forEach { (type, titleRes) ->
            root.addView(debugRow(context, tokens, context.getString(titleRes), type == current) {
                sheet.dismiss()
                onSelected(type)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 46f)
            ))
        }
        root.addView(debugRow(context, tokens, context.getString(R.string.weather_debug_clear), current == null) {
            sheet.dismiss()
            onSelected(null)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 46f)
        ))
        sheet.setContentView(root)
        sheet.show()
    }

    private fun debugRow(
        context: Context,
        tokens: ThemeTokens,
        text: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        this.text = if (selected) "✓  $text" else text
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(if (selected) tokens.accent else tokens.textPrimary)
        setPadding(dp(context, 8f), 0, dp(context, 8f), 0)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
