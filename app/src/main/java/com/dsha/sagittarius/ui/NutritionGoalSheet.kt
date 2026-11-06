package com.dsha.sagittarius.ui

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.NutritionGoalEntity
import com.google.android.material.bottomsheet.BottomSheetDialog

object NutritionGoalSheet {
    fun show(context: Context, goal: NutritionGoalEntity, onSave: (NutritionGoalEntity) -> Unit) {
        lateinit var sheet: BottomSheetDialog
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val tokens = ThemeTokens.of(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(22f))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.nutrition_goal_title)
            setTextColor(tokens.textPrimary)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        fun field(label: String, value: Double): EditText {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(8f), 0, 0)
            }
            row.addView(TextView(context).apply {
                text = label
                setTextColor(tokens.textSecondary)
                textSize = 13f
            }, LinearLayout.LayoutParams(0, dp(42f), 1f))
            val edit = EditText(context).apply {
                setText(value.toString())
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(tokens.textPrimary)
                textSize = 14f
                gravity = android.view.Gravity.END
            }
            row.addView(edit, LinearLayout.LayoutParams(dp(110f), dp(42f)))
            root.addView(row)
            return edit
        }
        val calories = field("热量 (kcal)", goal.calories)
        val carbs = field("碳水 (g)", goal.carbs)
        val fat = field("脂肪 (g)", goal.fat)
        val protein = field("蛋白质 (g)", goal.protein)
        val fiber = field("纤维 (g)", goal.fiber)
        val vitamin = field("维生素 (mg)", goal.vitamin)
        val water = field("水 (g)", goal.water)
        val save = TextView(context).apply {
            text = "保存目标"
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tokens.gradientStart, tokens.gradientMid)
            ).apply { cornerRadius = dp(14f).toFloat() }
            setOnClickListener {
                fun d(e: EditText): Double = e.text?.toString()?.toDoubleOrNull() ?: 0.0
                onSave(
                    NutritionGoalEntity(
                        calories = d(calories),
                        carbs = d(carbs),
                        fat = d(fat),
                        protein = d(protein),
                        fiber = d(fiber),
                        vitamin = d(vitamin),
                        water = d(water)
                    )
                )
                sheet.dismiss()
            }
        }
        root.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
            topMargin = dp(16f)
        })
        val scroll = ScrollView(context).apply { addView(root) }
        sheet = BottomSheetDialog(context).apply {
            setContentView(scroll)
        }
        sheet.show()
    }
}
