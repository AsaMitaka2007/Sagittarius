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

    @Test
    fun sodiumAndVitaminsScaleWithGrams() {
        val food = egg.copy(
            sodiumPer100g = 200.0,
            vitaminAPer100g = 100.0,
            vitaminB1Per100g = 0.5,
            vitaminB2Per100g = 0.4,
            vitaminB3Per100g = 2.0,
            vitaminCPer100g = 10.0,
            vitaminEPer100g = 1.0
        )
        val n = NutritionMath.scale(food, 50.0)
        assertEquals(100.0, n.sodium, 0.0001)
        assertEquals(50.0, n.vitaminA, 0.0001)
        assertEquals(0.25, n.vitaminB1, 0.0001)
        assertEquals(5.0, n.vitaminC, 0.0001)
        assertEquals(0.5, n.vitaminE, 0.0001)
    }

    @Test
    fun recordSnapshotKeepsImageAndSodium() {
        val food = egg.copy(
            sodiumPer100g = 100.0,
            imageUri = "asset:food_images/food_01.jpg"
        )
        val record = NutritionMath.recordFrom(food, MealType.LUNCH, 100.0, "g", 1.0, LocalDate.now())
        assertEquals(100.0, record.sodium, 0.0001)
        assertEquals("asset:food_images/food_01.jpg", record.imageUri)
    }

    @Test
    fun sumAggregatesSodiumAndVitamins() {
        val first = NutritionMath.recordFrom(
            egg.copy(sodiumPer100g = 10.0, vitaminCPer100g = 2.0),
            MealType.BREAKFAST, 100.0, "g", 1.0, LocalDate.now()
        )
        val second = NutritionMath.recordFrom(
            egg.copy(sodiumPer100g = 30.0, vitaminCPer100g = 4.0),
            MealType.LUNCH, 200.0, "g", 1.0, LocalDate.now()
        )
        val total = NutritionMath.sum(listOf(first, second))
        assertEquals(70.0, total.sodium, 0.0001)
        assertEquals(10.0, total.vitaminC, 0.0001)
    }
}
