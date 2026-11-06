package com.dsha.sagittarius.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.FoodUnitEntity
import com.dsha.sagittarius.data.NutritionMath
import com.dsha.sagittarius.data.NutritionValues
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

/** 轻量快捷录入：实时热量 + 可展开六项营养。 */
object FoodAmountSheet {
    fun show(
        context: Context,
        food: FoodEntity,
        units: List<FoodUnitEntity>,
        onConfirm: (amount: Double, unit: FoodUnitEntity) -> Unit
    ) {
        val safeUnits = if (units.any { it.unitName == "g" }) units else listOf(
            FoodUnitEntity(foodId = food.id, unitName = "g", weightInGrams = 1.0)
        ) + units
        val tokens = ThemeTokens.of(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val sheet = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(20f))
        }
        root.addView(TextView(context).apply {
            text = food.name
            textSize = 18f
            setTextColor(tokens.textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val amount = EditText(context).apply {
            hint = "数量"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (safeUnits.first().unitName == "g") "100" else "1")
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            textSize = 24f
            gravity = Gravity.START
        }
        root.addView(amount, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56f)))
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, safeUnits.map { it.unitName })
        root.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)))
        val kcal = TextView(context).apply {
            setTextColor(tokens.textPrimary)
            textSize = 34f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12f), 0, 0)
        }
        root.addView(kcal)
        root.addView(TextView(context).apply {
            text = "kcal"
            setTextColor(tokens.textTertiary)
            textSize = 11f
            gravity = Gravity.CENTER
        })
        val toggle = TextView(context).apply {
            text = "展开查看详细营养  ▼"
            gravity = Gravity.CENTER
            setTextColor(tokens.accent)
            textSize = 12f
            setPadding(0, dp(10f), 0, dp(6f))
        }
        root.addView(toggle)
        val grid = GridLayout(context).apply {
            columnCount = 2
            visibility = android.view.View.GONE
            setPadding(0, dp(4f), 0, dp(4f))
        }
        val detailKeys = listOf("碳水", "脂肪", "蛋白质", "纤维素", "维生素", "水")
        val detailViews = mutableListOf<TextView>()
        detailKeys.forEach { label ->
            val cell = TextView(context).apply {
                text = "$label  --"
                setTextColor(tokens.textSecondary)
                textSize = 12f
                setPadding(dp(6f), dp(7f), dp(6f), dp(7f))
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(38f)
            }
            grid.addView(cell, params)
            detailViews += cell
        }
        root.addView(grid)
        val confirm = TextView(context).apply {
            text = "确认添加"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tokens.gradientStart, tokens.gradientMid)
            ).apply { cornerRadius = dp(15f).toFloat() }
        }
        root.addView(confirm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
            topMargin = dp(14f)
        })
        sheet.setContentView(ScrollView(context).apply { addView(root) })
        var expanded = false
        fun currentValues(): Pair<Double, NutritionValues> {
            val qty = amount.text?.toString()?.toDoubleOrNull() ?: 0.0
            val unit = safeUnits.getOrElse(spinner.selectedItemPosition) { safeUnits.first() }
            val grams = NutritionMath.gramsFor(qty, unit.weightInGrams)
            return grams to NutritionMath.scale(food, grams)
        }
        fun update() {
            val (_, n) = currentValues()
            kcal.text = String.format(Locale.CHINA, "%.1f", n.calories)
            val values = listOf(n.carbs, n.fat, n.protein, n.fiber, n.vitamin, n.water)
            detailViews.forEachIndexed { index, view ->
                val suffix = if (index == 4) "mg" else "g"
                view.text = "${detailKeys[index]}  ${String.format(Locale.CHINA, "%.1f", values[index])} $suffix"
            }
        }
        fun toggleDetails() {
            expanded = !expanded
            grid.visibility = android.view.View.VISIBLE
            grid.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(root.width, android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val target = if (expanded) grid.measuredHeight else 0
            val start = grid.height
            android.animation.ValueAnimator.ofInt(start, target).apply {
                duration = 220L
                addUpdateListener { anim ->
                    val lp = grid.layoutParams
                    lp.height = anim.animatedValue as Int
                    grid.layoutParams = lp
                }
                start()
            }.also { animator ->
                if (!expanded) {
                    animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            grid.visibility = android.view.View.GONE
                        }
                    })
                }
            }
            toggle.text = if (expanded) "收起详细营养  ▲" else "展开查看详细营养  ▼"
        }
        amount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = update()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val unit = safeUnits.getOrElse(position) { safeUnits.first() }
                // 切换单位时回到该单位默认值：g=100，其他自定义单位=1。
                amount.setText(if (unit.unitName == "g") "100" else "1")
                amount.setSelection(amount.text?.length ?: 0)
                update()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        })
        toggle.setOnClickListener { toggleDetails() }
        confirm.setOnClickListener {
            val qty = amount.text?.toString()?.toDoubleOrNull() ?: 0.0
            if (qty <= 0.0) {
                amount.error = "请输入大于 0 的数量"
                return@setOnClickListener
            }
            val unit = safeUnits.getOrElse(spinner.selectedItemPosition) { safeUnits.first() }
            onConfirm(qty, unit)
            sheet.dismiss()
        }
        update()
        sheet.show()
    }
}
