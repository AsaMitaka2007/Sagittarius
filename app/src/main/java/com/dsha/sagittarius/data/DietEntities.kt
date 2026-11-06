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
    val icon: String = "🍽️",
    /** 默认单位，如 g、份。 */
    val unit: String = "g",
    /** 1 个 unit 等于多少克；g 单位时为 1。 */
    val weightPerUnit: Double = 1.0,
    val caloriesPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val proteinPer100g: Double,
    val fiberPer100g: Double,
    val vitaminPer100g: Double,
    val waterPer100g: Double,
    /** FRUIT_VEG / MEAT_EGG / STAPLE / SNACK */
    val category: String = "STAPLE",
    /** 只有收藏食材才进入饮食轮盘。 */
    val favorite: Boolean = false,
    /** 本地裁剪后的图标文件路径。 */
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
    val icon: String,
    val mealType: MealType,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val calories: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
    val fiber: Double,
    val vitamin: Double,
    val water: Double,
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
    val water: Double = 2000.0
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
            water = food.waterPer100g * factor
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
            icon = food.icon,
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
            date = date
        )
    }
}

data class NutritionValues(
    val calories: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
    val fiber: Double,
    val vitamin: Double,
    val water: Double
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
    val water: Double = 2000.0
) {
    fun toGoal(): NutritionGoalEntity = NutritionGoalEntity(
        calories = calories,
        carbs = carbs,
        fat = fat,
        protein = protein,
        fiber = fiber,
        vitamin = vitamin,
        water = water
    )
}
