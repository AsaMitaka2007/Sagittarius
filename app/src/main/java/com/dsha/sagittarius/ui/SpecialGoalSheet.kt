package com.dsha.sagittarius.ui

import android.app.DatePickerDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.NutritionGoalEntity
import com.dsha.sagittarius.data.NutritionGoalOverrideEntity
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SpecialGoalSheet {
    fun show(
        context: Context,
        date: LocalDate,
        base: NutritionGoalEntity,
        onSave: (NutritionGoalOverrideEntity) -> Unit
    ) {
        val tokens = ThemeTokens.of(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(22f))
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.nutrition_special_goal_title)
            textSize = 17f
            setTextColor(tokens.textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        var start = date
        var end = date
        val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        val dateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, 0)
        }
        val startView = TextView(context).apply {
            text = "${context.getString(R.string.nutrition_goal_start)}  ${start.format(fmt)}"
            setTextColor(tokens.textPrimary)
            textSize = 13f
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        val endView = TextView(context).apply {
            text = "${context.getString(R.string.nutrition_goal_end)}  ${end.format(fmt)}"
            setTextColor(tokens.textPrimary)
            textSize = 13f
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        dateRow.addView(startView, LinearLayout.LayoutParams(0, dp(42f), 1f))
        dateRow.addView(endView, LinearLayout.LayoutParams(0, dp(42f), 1f))
        root.addView(dateRow)
        fun pick(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
            DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
                initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }
        startView.setOnClickListener {
            pick(start) { value ->
                start = value
                if (end < start) end = start
                startView.text = "${context.getString(R.string.nutrition_goal_start)}  ${start.format(fmt)}"
                endView.text = "${context.getString(R.string.nutrition_goal_end)}  ${end.format(fmt)}"
            }
        }
        endView.setOnClickListener {
            pick(end) { value ->
                end = if (value < start) start else value
                endView.text = "${context.getString(R.string.nutrition_goal_end)}  ${end.format(fmt)}"
            }
        }
        fun field(label: String, value: Double): EditText {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3f), 0, 0)
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
                gravity = Gravity.END
            }
            row.addView(edit, LinearLayout.LayoutParams(dp(118f), dp(42f)))
            root.addView(row)
            return edit
        }
        val calories = field("热量 (kcal)", base.calories)
        val carbs = field("碳水 (g)", base.carbs)
        val fat = field("脂肪 (g)", base.fat)
        val protein = field("蛋白质 (g)", base.protein)
        val fiber = field("纤维 (g)", base.fiber)
        val vitamin = field("维生素 (mg)", base.vitamin)
        val water = field("水 (g)", base.water)
        val save = TextView(context).apply {
            text = "保存特殊目标"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tokens.gradientStart, tokens.gradientMid)
            ).apply { cornerRadius = dp(14f).toFloat() }
        }
        root.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
            topMargin = dp(14f)
        })
        val sheet = BottomSheetDialog(context)
        fun d(e: EditText): Double = e.text?.toString()?.toDoubleOrNull() ?: 0.0
        save.setOnClickListener {
            onSave(
                NutritionGoalOverrideEntity(
                    startDate = start,
                    endDate = end,
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
        sheet.setContentView(ScrollView(context).apply { addView(root) })
        sheet.show()
    }
}
