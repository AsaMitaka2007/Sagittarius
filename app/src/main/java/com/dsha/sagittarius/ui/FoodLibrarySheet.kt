package com.dsha.sagittarius.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.FoodCategoryEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.data.NutritionMath
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

object FoodLibrarySheet {
    fun show(
        context: Context,
        repository: DietRepository,
        date: LocalDate,
        meal: MealType,
        onQuickAdd: (com.dsha.sagittarius.data.DietRecordEntity) -> Unit
    ) {
        val tokens = ThemeTokens.of(context)
        val density = context.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var categories: List<FoodCategoryEntity> = emptyList()
        var selectedKey = "FRUIT_VEG"
        var allFoods: List<FoodEntity> = emptyList()
        val sheet = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(20f))
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = "食材库"
            textSize = 19f
            setTextColor(tokens.textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(44f), 1f))
        val editCategories = TextView(context).apply {
            text = "编辑分类"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(tokens.textSecondary)
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        titleRow.addView(editCategories, LinearLayout.LayoutParams(dp(82f), dp(40f)))
        val add = TextView(context).apply {
            text = "＋ 添加"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(tokens.accent)
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        titleRow.addView(add, LinearLayout.LayoutParams(dp(72f), dp(40f)))
        root.addView(titleRow)

        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f), 0, dp(8f))
        }
        root.addView(tabs)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(420f)
        ))
        root.addView(TextView(context).apply {
            text = "完成，返回记饮食"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tokens.gradientStart, tokens.gradientMid)
            ).apply { cornerRadius = dp(15f).toFloat() }
            setOnClickListener { sheet.dismiss() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)
        ).apply { topMargin = dp(10f) })
        sheet.setContentView(root)
        sheet.setOnDismissListener { scope.cancel() }

        var render: () -> Unit = {}
        var updateTabs: () -> Unit = {}

        fun addCategoryDialog() {
            val input = EditText(context).apply {
                hint = "分类名称"
                inputType = InputType.TYPE_CLASS_TEXT
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                setPadding(dp(18f), dp(10f), dp(18f), dp(4f))
            }
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("新增分类")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = input.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty()) {
                        input.error = "请输入分类名称"
                        return@setOnClickListener
                    }
                    scope.launch {
                        repository.addCategory(name, "CUSTOM_${System.currentTimeMillis()}")
                        dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }

        fun renameCategory(category: FoodCategoryEntity) {
            val input = EditText(context).apply {
                setText(category.name)
                setTextColor(tokens.textPrimary)
                setPadding(dp(18f), dp(10f), dp(18f), dp(4f))
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("重命名分类")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存") { _, _ ->
                    val name = input.text?.toString()?.trim().orEmpty()
                    if (name.isNotEmpty()) scope.launch { repository.renameCategory(category, name) }
                }
                .show()
        }

        fun manageCategories() {
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18f), dp(8f), dp(18f), dp(4f))
            }
            categories.forEach { category ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(5f), 0, dp(5f))
                }
                row.addView(TextView(context).apply {
                    text = category.name + if (category.isPreset) "（预置）" else ""
                    setTextColor(tokens.textPrimary)
                    textSize = 14f
                }, LinearLayout.LayoutParams(0, dp(40f), 1f))
                if (!category.isPreset) {
                    row.addView(TextView(context).apply {
                        text = "重命名"
                        setTextColor(tokens.accent)
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding(dp(6f), 0, dp(6f), 0)
                        setOnClickListener { renameCategory(category) }
                    }, LinearLayout.LayoutParams(dp(64f), dp(40f)))
                    row.addView(TextView(context).apply {
                        text = "删除"
                        setTextColor(Color.parseColor("#D9534F"))
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setPadding(dp(6f), 0, dp(6f), 0)
                        setOnClickListener {
                            Haptics.longPress(root)
                            scope.launch {
                                val deleted = repository.deleteCategory(category)
                                Toast.makeText(
                                    context,
                                    if (deleted) "分类已删除" else "请先移除该分类下的食材",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }, LinearLayout.LayoutParams(dp(56f), dp(40f)))
                }
                content.addView(row)
            }
            MaterialAlertDialogBuilder(context)
                .setTitle("分类管理")
                .setView(ScrollView(context).apply { addView(content) })
                .setNegativeButton("关闭", null)
                .setPositiveButton("新增分类") { _, _ -> addCategoryDialog() }
                .show()
        }

        fun deleteFood(food: FoodEntity) {
            Haptics.longPress(sheet.window?.decorView ?: root)
            MaterialAlertDialogBuilder(context)
                .setTitle("删除食材")
                .setMessage("确认删除「${food.name}」？删除不影响历史记录，并会同步取消轮盘收藏。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除") { _, _ ->
                    scope.launch {
                        repository.deleteFood(food)
                        Haptics.tap(root)
                        Toast.makeText(context, "食材已删除，历史记录保留", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        fun quickAdd(food: FoodEntity) {
            scope.launch {
                val units = repository.getUnits(food.id)
                FoodAmountSheet.show(context, food, units) { amount, unit ->
                    val record = NutritionMath.recordFrom(
                        food, meal, amount, unit.unitName, unit.weightInGrams, date
                    )
                    onQuickAdd(record)
                    Haptics.tap(root)
                    Toast.makeText(context, "已加入气泡 · 返回后保存", Toast.LENGTH_SHORT).show()
                }
            }
        }

        updateTabs = {
            tabs.removeAllViews()
            categories.forEach { category ->
                val selected = category.categoryKey == selectedKey
                val tab = TextView(context).apply {
                    text = category.name
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(if (selected) Color.WHITE else tokens.textSecondary)
                    background = if (selected) {
                        GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(tokens.gradientStart, tokens.gradientMid)
                        ).apply { cornerRadius = dp(12f).toFloat() }
                    } else null
                    setOnClickListener {
                        selectedKey = category.categoryKey
                        updateTabs()
                        render()
                    }
                    setOnLongClickListener {
                        if (!category.isPreset) manageCategories()
                        true
                    }
                }
                tabs.addView(tab, LinearLayout.LayoutParams(0, dp(36f), 1f))
            }
        }

        render = {
            list.removeAllViews()
            val foods = allFoods.filter { it.category == selectedKey }
            if (foods.isEmpty()) {
                list.addView(TextView(context).apply {
                    text = "这个分类还没有食材"
                    gravity = Gravity.CENTER
                    setTextColor(tokens.textTertiary)
                    textSize = 12f
                    setPadding(0, dp(36f), 0, dp(36f))
                })
            } else {
                foods.forEach { food ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(7f), 0, dp(7f))
                    }
                    val image = ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(tokens.iconCircle)
                        contentDescription = "更换 ${food.name} 图片"
                        setOnClickListener {
                            context.startActivity(
                                Intent(context, FoodEditActivity::class.java)
                                    .putExtra(FoodEditActivity.EXTRA_FOOD_ID, food.id)
                                    .putExtra(FoodEditActivity.EXTRA_CATEGORY, food.category)
                            )
                        }
                    }
                    val bitmap = food.imageUri?.let { BitmapFactory.decodeFile(it) }
                    if (bitmap != null) image.setImageBitmap(bitmap)
                    else image.setImageResource(android.R.drawable.ic_menu_gallery)
                    row.addView(image, LinearLayout.LayoutParams(dp(42f), dp(42f)))
                    val info = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(10f), 0, 0, 0)
                    }
                    info.addView(TextView(context).apply {
                        text = "${food.icon} ${food.name}"
                        setTextColor(tokens.textPrimary)
                        textSize = 14f
                    })
                    info.addView(TextView(context).apply {
                        text = String.format(Locale.CHINA, "%.0f kcal / 100g", food.caloriesPer100g)
                        setTextColor(tokens.textTertiary)
                        textSize = 11f
                        setPadding(0, dp(2f), 0, 0)
                    })
                    row.addView(info, LinearLayout.LayoutParams(0, dp(42f), 1f))
                    val plus = TextView(context).apply {
                        text = "＋"
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTextColor(tokens.accent)
                        setOnClickListener { quickAdd(food) }
                    }
                    row.addView(plus, LinearLayout.LayoutParams(dp(42f), dp(42f)))
                    val star = TextView(context).apply {
                        text = if (food.favorite) "★" else "☆"
                        textSize = 23f
                        gravity = Gravity.CENTER
                        setTextColor(if (food.favorite) Color.parseColor("#FFB84D") else tokens.textTertiary)
                        setOnClickListener {
                            Haptics.tap(it)
                            scope.launch { repository.setFavorite(food, !food.favorite) }
                        }
                    }
                    row.addView(star, LinearLayout.LayoutParams(dp(40f), dp(42f)))
                    row.setOnLongClickListener { deleteFood(food); true }
                    list.addView(row)
                }
            }
        }

        editCategories.setOnClickListener { manageCategories() }
        add.setOnClickListener {
            context.startActivity(
                Intent(context, FoodEditActivity::class.java)
                    .putExtra(FoodEditActivity.EXTRA_CATEGORY, selectedKey)
            )
        }
        scope.launch {
            repository.categories.collectLatest { value ->
                categories = value
                if (categories.none { it.categoryKey == selectedKey }) {
                    selectedKey = categories.firstOrNull()?.categoryKey ?: "FRUIT_VEG"
                }
                updateTabs()
                render()
            }
        }
        scope.launch {
            repository.foods.collectLatest { foods ->
                allFoods = foods
                render()
            }
        }
        sheet.show()
    }

    private fun mealLabel(meal: MealType): String = when (meal) {
        MealType.BREAKFAST -> "早餐"
        MealType.LUNCH -> "午餐"
        MealType.DINNER -> "晚餐"
        MealType.SNACK -> "加餐"
    }
}
