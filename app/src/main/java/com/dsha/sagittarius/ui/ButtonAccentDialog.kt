package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 与顶栏日期色盘同款的按钮颜色选择盘：5 个渐变预设 + 自定义颜色。 */
object ButtonAccentDialog {
    fun show(context: Context, onPick: (ButtonAccentStyle) -> Unit) {
        val tokens = ThemeTokens.of(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12f), dp(context, 8f), dp(context, 12f), dp(context, 6f))
        }
        val grid = GridLayout(context).apply {
            columnCount = 3
            setPadding(dp(context, 4f), dp(context, 4f), dp(context, 4f), dp(context, 4f))
        }
        var dialog: androidx.appcompat.app.AlertDialog? = null
        ButtonAccentManager.presets.forEachIndexed { index, item ->
            val swatch = TextView(context).apply {
                text = item.label
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(item.textColor)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(item.startColor, item.endColor)
                ).apply {
                    cornerRadius = dp(context, 12f).toFloat()
                    setStroke(dp(context, 1f), ColorUtils.setAlphaComponent(Color.WHITE, 60))
                }
                setOnClickListener {
                    onPick(item)
                    dialog?.dismiss()
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 3),
                GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = dp(context, 52f)
                setMargins(dp(context, 4f), dp(context, 4f), dp(context, 4f), dp(context, 4f))
            }
            grid.addView(swatch, params)
        }
        root.addView(grid)
        val custom = TextView(context).apply {
            text = context.getString(R.string.button_color_custom)
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(tokens.accent)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 12f).toFloat()
                setColor(ColorUtils.setAlphaComponent(tokens.accent, 24))
            }
            setOnClickListener {
                showCustom(context, ButtonAccentManager.current) { picked ->
                    onPick(picked)
                    dialog?.dismiss()
                }
            }
        }
        root.addView(custom, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44f)
        ).apply { topMargin = dp(context, 6f) })
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.button_color_title)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun showCustom(
        context: Context,
        initial: ButtonAccentStyle,
        onPick: (ButtonAccentStyle) -> Unit
    ) {
        val tokens = ThemeTokens.of(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22f), dp(context, 10f), dp(context, 22f), dp(context, 8f))
        }
        val preview = TextView(context).apply {
            text = "Aa"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(initial.startColor, initial.endColor)
            ).apply { cornerRadius = dp(context, 14f).toFloat() }
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 58f)
        ))
        val start = intArrayOf(Color.red(initial.startColor), Color.green(initial.startColor), Color.blue(initial.startColor))
        val bars = mutableListOf<SeekBar>()
        listOf("R", "G", "B").forEachIndexed { index, label ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply {
                text = label
                setTextColor(tokens.textSecondary)
                textSize = 12f
            }, LinearLayout.LayoutParams(dp(context, 22f), dp(context, 30f)))
            val bar = SeekBar(context).apply {
                max = 255
                progress = start[index]
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        start[index] = progress
                        val color = Color.rgb(start[0], start[1], start[2])
                        preview.background = GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(color, ColorUtils.blendARGB(color, Color.WHITE, 0.22f))
                        ).apply { cornerRadius = dp(context, 14f).toFloat() }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
            bars.add(bar)
            row.addView(bar, LinearLayout.LayoutParams(0, dp(context, 38f), 1f))
            root.addView(row)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.button_color_custom)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val color = Color.rgb(start[0], start[1], start[2])
                onPick(ButtonAccentManager.custom(color, ColorUtils.blendARGB(color, Color.WHITE, 0.22f)))
            }
            .create()
        dialog.show()
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
