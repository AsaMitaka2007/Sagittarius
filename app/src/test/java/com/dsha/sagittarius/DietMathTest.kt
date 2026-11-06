package com.dsha.sagittarius

import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.data.NutritionMath
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DietMathTest {

    private val egg = FoodEntity(
        id = 1,
        name = "鸡蛋",
        caloriesPer100g = 144.0,
        carbsPer100g = 1.3,
        fatPer100g = 8.8,
        proteinPer100g = 13.3,
        fiberPer100g = 0.0,
        vitaminPer100g = 0.5,
        waterPer100g = 74.0
    )

    @Test
    fun gramsUnitNeverMultipliesByHundred() {
        assertEquals(100.0, NutritionMath.gramsFor(100.0, 1.0), 0.0001)
        assertEquals(1.0, NutritionMath.gramsFor(1.0, 1.0), 0.0001)
    }

    @Test
    fun oneEggUsesSixtyGramCustomUnit() {
        val record = NutritionMath.recordFrom(egg, MealType.BREAKFAST, 1.0, "个", 60.0, LocalDate.now())
        assertEquals(60.0, record.grams, 0.0001)
        assertEquals(86.4, record.calories, 0.0001)
    }
}
