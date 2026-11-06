package com.dsha.sagittarius.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.NutritionMath
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object DietAmountDialog {
    fun show(
        context: Context,
        food: FoodEntity,
        onConfirm: (amount: Double, unit: String) -> Unit
    ) {
        val tokens = ThemeTokens.of(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), 0)
        }
        val amount = EditText(context).apply {
            hint = context.getString(R.string.diet_amount_label)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            textSize = 20f
            gravity = Gravity.START
        }
        amount.setText("100")
        root.addView(amount, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50f)))
        val unitSpinner = Spinner(context)
        val units = listOf("g", "份", "碗", "100g", food.unit).distinct()
        unitSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, units)
        root.addView(unitSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
            topMargin = dp(6f)
        })
        val preview = TextView(context).apply {
            setTextColor(tokens.textSecondary)
            textSize = 12f
            setPadding(0, dp(8f), 0, dp(4f))
        }
        root.addView(preview)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.diet_amount_title, food.name))
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.diet_save, null)
            .create()
        fun update() {
            val value = amount.text?.toString()?.toDoubleOrNull() ?: 0.0
            val unit = unitSpinner.selectedItem?.toString() ?: "g"
            val grams = NutritionMath.gramsFor(food, value, unit)
            val kcal = food.caloriesPer100g * grams / 100.0
            preview.text = context.getString(R.string.diet_amount_preview, grams, kcal)
        }
        amount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        unitSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = update()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
        update()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (value <= 0.0) {
                    amount.error = "请输入大于 0 的摄入量"
                    return@setOnClickListener
                }
                val unit = unitSpinner.selectedItem?.toString() ?: "g"
                dialog.dismiss()
                onConfirm(value, unit)
            }
        }
        dialog.show()
    }
}

