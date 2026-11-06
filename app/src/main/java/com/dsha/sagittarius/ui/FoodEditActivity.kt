package com.dsha.sagittarius.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.FoodUnitEntity
import kotlinx.coroutines.launch

class FoodEditActivity : ComponentActivity() {

    private lateinit var repository: DietRepository
    private var category: String = "STAPLE"
    private var editingId = 0L
    private var existing: FoodEntity? = null
    private var imagePath: String? = null
    private lateinit var preview: ImageView
    private lateinit var unitsContainer: LinearLayout
    private lateinit var titleView: TextView
    private val fields = mutableMapOf<String, EditText>()
    private val units = mutableListOf<FoodUnitEntity>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imagePath = result.data?.getStringExtra(ImageCropActivity.EXTRA_OUTPUT_PATH)
            renderPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as SagittariusApp).dietRepository
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: "STAPLE"
        editingId = intent.getLongExtra(EXTRA_FOOD_ID, 0L)
        val tokens = ThemeTokens.of(this)
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(30f))
        }
        titleView = TextView(this).apply {
            text = if (editingId > 0) "编辑食材" else "添加食材"
            textSize = 22f
            setTextColor(tokens.textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(titleView)
        root.addView(TextView(this).apply {
            text = "所有营养参数统一按每 100g 食物录入"
            textSize = 11f
            setTextColor(tokens.textTertiary)
            setPadding(0, dp(4f), 0, dp(8f))
        })
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(tokens.iconCircle)
            contentDescription = "食材图片，点击更换"
            setOnClickListener { chooseImage() }
        }
        root.addView(preview, LinearLayout.LayoutParams(dp(82f), dp(82f)).apply {
            topMargin = dp(6f)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.addView(TextView(this).apply {
            text = "点击图片上传 / 更换"
            gravity = Gravity.CENTER
            setTextColor(tokens.accent)
            textSize = 13f
            setPadding(0, dp(6f), 0, dp(8f))
            setOnClickListener { chooseImage() }
        })
        field(root, "name", "食物名称", InputType.TYPE_CLASS_TEXT, "")
        field(root, "icon", "图标（emoji）", InputType.TYPE_CLASS_TEXT, "🍽️")
        field(root, "kcal", "热量", num(), "0", "kcal / 100g")
        field(root, "carbs", "碳水化合物", num(), "0", "g / 100g")
        field(root, "fat", "脂肪", num(), "0", "g / 100g")
        field(root, "protein", "蛋白质", num(), "0", "g / 100g")
        field(root, "fiber", "纤维素", num(), "0", "g / 100g")
        field(root, "vitamin", "维生素", num(), "0", "mg / 100g")
        field(root, "water", "水", num(), "0", "g / 100g")

        root.addView(TextView(this).apply {
            text = "单位换算"
            textSize = 15f
            setTextColor(tokens.textPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(16f), 0, dp(2f))
        })
        root.addView(TextView(this).apply {
            text = "默认只有 g：1g = 1g。可添加仅属于当前食物的单位，例如 1个 = 60g。"
            textSize = 11f
            setTextColor(tokens.textTertiary)
            setPadding(0, 0, 0, dp(6f))
        })
        unitsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(unitsContainer)
        root.addView(TextView(this).apply {
            text = "＋ 添加单位换算"
            gravity = Gravity.CENTER
            setTextColor(tokens.accent)
            textSize = 13f
            setPadding(0, dp(10f), 0, dp(10f))
            setOnClickListener {
                units.add(FoodUnitEntity(foodId = editingId, unitName = "", weightInGrams = 0.0))
                renderUnits()
            }
        })
        val save = TextView(this).apply {
            text = "保存食材"
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(tokens.gradientStart, tokens.gradientMid)
            ).apply { cornerRadius = dp(16f).toFloat() }
            setOnClickListener { saveFood() }
        }
        root.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)).apply {
            topMargin = dp(18f)
        })
        setContentView(ScrollView(this).apply { addView(root) })
        renderUnits()
        renderPreview()
        if (editingId > 0) {
            lifecycleScope.launch {
                repository.getFood(editingId)?.let { food ->
                    existing = food
                    category = food.category
                    populate(food)
                    units.clear()
                    units.addAll(repository.getUnits(food.id).filter { it.unitName != "g" })
                    renderUnits()
                }
            }
        }
    }

    private fun chooseImage() {
        pickImage.launch(ImageCropActivity.intent(this, 1, 1, false, "food"))
    }

    private fun num(): Int = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

    private fun field(root: LinearLayout, key: String, label: String, type: Int, initial: String, suffix: String? = null) {
        val tokens = ThemeTokens.of(this)
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3f), 0, 0)
        }
        val labelView = TextView(this).apply {
            text = if (suffix == null) label else "$label ($suffix)"
            setTextColor(tokens.textSecondary)
            textSize = 12f
        }
        row.addView(labelView, LinearLayout.LayoutParams(0, dp(46f), 1f))
        val edit = EditText(this).apply {
            inputType = type
            setText(initial)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textTertiary)
            textSize = 14f
            gravity = Gravity.END
        }
        fields[key] = edit
        row.addView(edit, LinearLayout.LayoutParams(dp(150f), dp(46f)))
        root.addView(row)
    }

    private fun renderUnits() {
        val tokens = ThemeTokens.of(this)
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()
        unitsContainer.removeAllViews()
        unitsContainer.addView(TextView(this).apply {
            text = "g  ·  1g = 1g（基础单位）"
            setTextColor(tokens.textTertiary)
            textSize = 12f
            setPadding(0, dp(6f), 0, dp(6f))
        })
        units.forEachIndexed { index, unit ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3f), 0, dp(3f))
            }
            val name = EditText(this).apply {
                hint = "单位，如个"
                setText(unit.unitName)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                textSize = 13f
            }
            val weight = EditText(this).apply {
                hint = "克重，如 60"
                setText(if (unit.weightInGrams > 0) unit.weightInGrams.toString() else "")
                inputType = num()
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textTertiary)
                textSize = 13f
                gravity = Gravity.END
            }
            val delete = TextView(this).apply {
                text = "×"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(tokens.textTertiary)
                setOnClickListener {
                    units.removeAt(index)
                    renderUnits()
                }
            }
            fun sync() {
                if (index !in units.indices) return
                val n = name.text?.toString()?.trim().orEmpty()
                val w = weight.text?.toString()?.toDoubleOrNull() ?: 0.0
                units[index] = units[index].copy(unitName = n, weightInGrams = w)
            }
            name.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = sync()
                override fun afterTextChanged(s: Editable?) = Unit
            })
            weight.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = sync()
                override fun afterTextChanged(s: Editable?) = Unit
            })
            row.addView(name, LinearLayout.LayoutParams(0, dp(44f), 1f))
            row.addView(weight, LinearLayout.LayoutParams(dp(100f), dp(44f)))
            row.addView(delete, LinearLayout.LayoutParams(dp(38f), dp(44f)))
            unitsContainer.addView(row)
            // 初始同步，确保未失焦时也能保存
            sync()
        }
    }

    private fun populate(food: FoodEntity) {
        imagePath = food.imageUri
        fields["name"]?.setText(food.name)
        fields["icon"]?.setText(food.icon)
        fields["kcal"]?.setText(food.caloriesPer100g.toString())
        fields["carbs"]?.setText(food.carbsPer100g.toString())
        fields["fat"]?.setText(food.fatPer100g.toString())
        fields["protein"]?.setText(food.proteinPer100g.toString())
        fields["fiber"]?.setText(food.fiberPer100g.toString())
        fields["vitamin"]?.setText(food.vitaminPer100g.toString())
        fields["water"]?.setText(food.waterPer100g.toString())
        renderPreview()
    }

    private fun renderPreview() {
        val bitmap = imagePath?.let { BitmapFactory.decodeFile(it) }
        if (bitmap != null) preview.setImageBitmap(bitmap) else preview.setImageResource(android.R.drawable.ic_menu_gallery)
    }

    private fun saveFood() {
        fun text(key: String): String = fields[key]?.text?.toString()?.trim().orEmpty()
        fun number(key: String): Double = text(key).toDoubleOrNull() ?: 0.0
        val name = text("name")
        if (name.isEmpty()) {
            fields["name"]?.error = "请输入食材名称"
            return
        }
        val cleanUnits = units.filter {
            it.unitName.isNotBlank() && it.unitName != "g" && it.weightInGrams > 0
        }
        val base = existing
        val food = FoodEntity(
            id = base?.id ?: 0L,
            name = name,
            icon = text("icon").ifEmpty { "🍽️" },
            unit = "g",
            weightPerUnit = 1.0,
            caloriesPer100g = number("kcal"),
            carbsPer100g = number("carbs"),
            fatPer100g = number("fat"),
            proteinPer100g = number("protein"),
            fiberPer100g = number("fiber"),
            vitaminPer100g = number("vitamin"),
            waterPer100g = number("water"),
            category = base?.category ?: category,
            favorite = base?.favorite ?: false,
            imageUri = imagePath ?: base?.imageUri,
            isCustom = base?.isCustom ?: true,
            sortOrder = base?.sortOrder ?: 0,
            createdAt = base?.createdAt ?: System.currentTimeMillis()
        )
        lifecycleScope.launch {
            val savedId = if (base != null) {
                repository.updateFood(food)
                base.id
            } else {
                repository.addFood(food)?.id ?: 0L
            }
            if (savedId > 0) repository.replaceUnits(savedId, cleanUnits)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_FOOD_ID = "food_id"
    }
}
