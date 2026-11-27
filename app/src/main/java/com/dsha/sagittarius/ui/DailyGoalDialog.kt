package com.dsha.sagittarius.ui

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.dsha.sagittarius.data.DailyGoalMath
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** 新增当日目标：名称 + 营养素 + 数量。 */
object DailyGoalDialog {
    fun show(context: Context, onSave: (title: String, nutrientKey: String, amount: Double) -> Unit) {
        val tokens = ThemeTokens.of(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val labels = listOf("水 (g)", "蛋白质 (g)", "碳水 (g)", "脂肪 (g)", "纤维 (g)", "热量 (kcal)", "钠 (mg)")
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), dp(4f))
        }
        val title = EditText(context).apply {
            hint = "目标名称，如 每日 1500ml 水"
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            textSize = 14f
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)))
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        root.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)))
        val amount = EditText(context).apply {
            hint = "数量，如 1500"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            textSize = 14f
        }
        root.addView(amount, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)))
        root.addView(TextView(context).apply {
            text = "勾选后会把该数量加到今天对应营养素上"
            setTextColor(tokens.textTertiary)
            textSize = 11f
            setPadding(0, dp(2f), 0, 0)
        })

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("新增当日目标")
            .setView(root)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = title.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    title.error = "请输入目标名称"
                    return@setOnClickListener
                }
                val value = amount.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (value <= 0.0) {
                    amount.error = "请输入大于 0 的数量"
                    return@setOnClickListener
                }
                val index = spinner.selectedItemPosition.coerceIn(0, DailyGoalMath.keys.size - 1)
                dialog.dismiss()
                onSave(name, DailyGoalMath.keys[index], value)
            }
        }
        dialog.show()
    }
}
