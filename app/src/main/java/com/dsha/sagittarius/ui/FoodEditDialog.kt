package com.dsha.sagittarius.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.FoodEntity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object FoodEditDialog {
    fun show(context: Context, onSave: (FoodEntity) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val tokens = ThemeTokens.of(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), dp(8f))
        }
        fun field(hintRes: Int, inputType: Int = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL, text: String = ""): EditText {
            val e = EditText(context).apply {
                hint = context.getString(hintRes)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                textSize = 14f
                this.inputType = inputType
                setText(text)
            }
            root.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)))
            return e
        }
        val name = field(R.string.diet_food_name, android.text.InputType.TYPE_CLASS_TEXT)
        val icon = field(R.string.diet_food_icon, android.text.InputType.TYPE_CLASS_TEXT, "🍽️")
        val unit = field(R.string.diet_food_unit, android.text.InputType.TYPE_CLASS_TEXT, "g")
        val weight = field(R.string.diet_food_weight, text = "1")
        val kcal = field(R.string.diet_food_kcal, text = "0")
        val carbs = field(R.string.diet_food_carbs, text = "0")
        val fat = field(R.string.diet_food_fat, text = "0")
        val protein = field(R.string.diet_food_protein, text = "0")
        val fiber = field(R.string.diet_food_fiber, text = "0")
        val vitamin = field(R.string.diet_food_vitamin, text = "0")
        val water = field(R.string.diet_food_water, text = "0")
        val scroll = ScrollView(context).apply { addView(root) }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.diet_add_custom_food)
            .setView(scroll)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.diet_food_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val foodName = name.text?.toString()?.trim().orEmpty()
                if (foodName.isEmpty()) {
                    name.error = "请输入食物名称"
                    return@setOnClickListener
                }
                fun d(e: EditText): Double = e.text?.toString()?.toDoubleOrNull() ?: 0.0
                val food = FoodEntity(
                    name = foodName,
                    icon = icon.text?.toString()?.trim().orEmpty().ifEmpty { "🍽️" },
                    unit = unit.text?.toString()?.trim().orEmpty().ifEmpty { "g" },
                    weightPerUnit = d(weight).takeIf { it > 0 } ?: 1.0,
                    caloriesPer100g = d(kcal),
                    carbsPer100g = d(carbs),
                    fatPer100g = d(fat),
                    proteinPer100g = d(protein),
                    fiberPer100g = d(fiber),
                    vitaminPer100g = d(vitamin),
                    waterPer100g = d(water),
                    isCustom = true
                )
                dialog.dismiss()
                onSave(food)
            }
        }
        dialog.show()
    }
}
