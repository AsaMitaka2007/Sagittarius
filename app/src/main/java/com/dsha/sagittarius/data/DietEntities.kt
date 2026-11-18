package com.dsha.sagittarius.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class MealType {
    BREAKFAST, LUNCH, DINNER, SNACK;

    companion object {
        fun fromStorage(value: String?): MealType? =
            entries.firstOrNull { it.name == value }
    }
}

@Entity(tableName = "foods", indices = [Index(value = ["name"], unique = true)])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** 已废弃：图标改由图片展示，字段仅为兼容旧库保留，新数据统一写空字符串。 */
    val icon: String = "",
    /** 默认单位，如 g、份。 */
    val unit: String = "g",
    /** 1 个 unit 等于多少克；g 单位时为 1。 */
    val weightPerUnit: Double = 1.0,
    val caloriesPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val proteinPer100g: Double,
    val fiberPer100g: Double,
    /** 维生素总量（mg），用于总体达标参考，不等于下方细分项之和。 */
    val vitaminPer100g: Double,
    val waterPer100g: Double,
    /** 钠（mg）。 */
    val sodiumPer100g: Double = 0.0,
    /** 维生素 A（μg RE）。 */
    val vitaminAPer100g: Double = 0.0,
    /** 维生素 B1（mg）。 */
    val vitaminB1Per100g: Double = 0.0,
    /** 维生素 B2（mg）。 */
    val vitaminB2Per100g: Double = 0.0,
    /** 烟酸 / 维生素 B3（mg）。 */
    val vitaminB3Per100g: Double = 0.0,
    /** 维生素 C（mg）。 */
    val vitaminCPer100g: Double = 0.0,
    /** 维生素 E（mg）。 */
    val vitaminEPer100g: Double = 0.0,
    /** FRUIT_VEG / MEAT_EGG / STAPLE / SNACK */
    val category: String = "STAPLE",
    /** 只有收藏食材才进入饮食轮盘。 */
    val favorite: Boolean = false,
    /** 本地图片路径，或 asset:food_images/xxx.jpg 形式的打包图片。 */
    val imageUri: String? = null,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "food_units",
    indices = [Index("foodId")]
)
data class FoodUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val foodId: Long,
    val unitName: String,
    val weightInGrams: Double
)

@Entity(
    tableName = "food_categories",
    indices = [Index(value = ["categoryKey"], unique = true)]
)
data class FoodCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryKey: String,
    val name: String,
    val isPreset: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "diet_records",
    indices = [Index("date"), Index("foodId")]
)
data class DietRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val foodId: Long,
    val foodName: String,
    /** 已废弃：图标改由 imageUri 展示，字段仅为兼容旧库保留，新数据统一写空字符串。 */
    val icon: String = "",
    val mealType: MealType,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val calories: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
    val fiber: Double,
    /** 维生素总量（mg）。 */
    val vitamin: Double,
    val water: Double,
    /** 钠（mg）。 */
    val sodium: Double = 0.0,
    /** 维生素 A（μg RE）。 */
    val vitaminA: Double = 0.0,
    /** 维生素 B1（mg）。 */
    val vitaminB1: Double = 0.0,
    /** 维生素 B2（mg）。 */
    val vitaminB2: Double = 0.0,
    /** 烟酸 / 维生素 B3（mg）。 */
    val vitaminB3: Double = 0.0,
    /** 维生素 C（mg）。 */
    val vitaminC: Double = 0.0,
    /** 维生素 E（mg）。 */
    val vitaminE: Double = 0.0,
    /** 记录时的图片快照，避免食材后续换图影响历史。 */
    val imageUri: String? = null,
    val date: LocalDate,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "nutrition_goals")
data class NutritionGoalEntity(
    @PrimaryKey val id: Int = 1,
    val calories: Double = 2000.0,
    val carbs: Double = 250.0,
    val fat: Double = 60.0,
    val protein: Double = 60.0,
    val fiber: Double = 25.0,
    val vitamin: Double = 100.0,
    val water: Double = 2000.0,
    /** 钠（mg），默认按中国居民膳食指南上限 2000mg。 */
    val sodium: Double = 2000.0
)

object NutritionMath {
    /** 数量 × 单位克重；g 单位克重必须为 1。 */
    fun gramsFor(amount: Double, unitWeightInGrams: Double): Double {
        val safeWeight = if (unitWeightInGrams <= 0.0) 1.0 else unitWeightInGrams
        return amount * safeWeight
    }

    fun gramsFor(food: FoodEntity, amount: Double, unit: String): Double = when {
        unit == "g" -> amount
        else -> amount * 1.0
    }

    fun scale(food: FoodEntity, grams: Double): NutritionValues {
        val factor = grams / 100.0
        return NutritionValues(
            calories = food.caloriesPer100g * factor,
            carbs = food.carbsPer100g * factor,
            fat = food.fatPer100g * factor,
            protein = food.proteinPer100g * factor,
            fiber = food.fiberPer100g * factor,
            vitamin = food.vitaminPer100g * factor,
            water = food.waterPer100g * factor,
            sodium = food.sodiumPer100g * factor,
            vitaminA = food.vitaminAPer100g * factor,
            vitaminB1 = food.vitaminB1Per100g * factor,
            vitaminB2 = food.vitaminB2Per100g * factor,
            vitaminB3 = food.vitaminB3Per100g * factor,
            vitaminC = food.vitaminCPer100g * factor,
            vitaminE = food.vitaminEPer100g * factor
        )
    }

    fun recordFrom(
        food: FoodEntity,
        mealType: MealType,
        amount: Double,
        unitName: String,
        unitWeightInGrams: Double,
        date: LocalDate
    ): DietRecordEntity {
        val grams = gramsFor(amount, unitWeightInGrams)
        val n = scale(food, grams)
        return DietRecordEntity(
            foodId = food.id,
            foodName = food.name,
            mealType = mealType,
            amount = amount,
            unit = unitName,
            grams = grams,
            calories = n.calories,
            carbs = n.carbs,
            fat = n.fat,
            protein = n.protein,
            fiber = n.fiber,
            vitamin = n.vitamin,
            water = n.water,
            sodium = n.sodium,
            vitaminA = n.vitaminA,
            vitaminB1 = n.vitaminB1,
            vitaminB2 = n.vitaminB2,
            vitaminB3 = n.vitaminB3,
            vitaminC = n.vitaminC,
            vitaminE = n.vitaminE,
            imageUri = food.imageUri,
            date = date
        )
    }

    /** 汇总一组饮食记录，供营养页与测试共用。 */
    fun sum(records: List<DietRecordEntity>): NutritionValues = NutritionValues(
        calories = records.sumOf { it.calories },
        carbs = records.sumOf { it.carbs },
        fat = records.sumOf { it.fat },
        protein = records.sumOf { it.protein },
        fiber = records.sumOf { it.fiber },
        vitamin = records.sumOf { it.vitamin },
        water = records.sumOf { it.water },
        sodium = records.sumOf { it.sodium },
        vitaminA = records.sumOf { it.vitaminA },
        vitaminB1 = records.sumOf { it.vitaminB1 },
        vitaminB2 = records.sumOf { it.vitaminB2 },
        vitaminB3 = records.sumOf { it.vitaminB3 },
        vitaminC = records.sumOf { it.vitaminC },
        vitaminE = records.sumOf { it.vitaminE }
    )
}

data class NutritionValues(
    val calories: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
    val fiber: Double,
    val vitamin: Double,
    val water: Double,
    val sodium: Double = 0.0,
    val vitaminA: Double = 0.0,
    val vitaminB1: Double = 0.0,
    val vitaminB2: Double = 0.0,
    val vitaminB3: Double = 0.0,
    val vitaminC: Double = 0.0,
    val vitaminE: Double = 0.0
)

@Entity(
    tableName = "nutrition_goal_overrides",
    indices = [Index("startDate", "endDate")]
)
data class NutritionGoalOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val calories: Double = 2000.0,
    val carbs: Double = 250.0,
    val fat: Double = 60.0,
    val protein: Double = 60.0,
    val fiber: Double = 25.0,
    val vitamin: Double = 100.0,
    val water: Double = 2000.0,
    val sodium: Double = 2000.0
) {
    fun toGoal(): NutritionGoalEntity = NutritionGoalEntity(
        calories = calories,
        carbs = carbs,
        fat = fat,
        protein = protein,
        fiber = fiber,
        vitamin = vitamin,
        water = water,
        sodium = sodium
    )
}
