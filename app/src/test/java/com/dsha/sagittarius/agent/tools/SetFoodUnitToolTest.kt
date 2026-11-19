package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.FoodUnitEntity
import com.dsha.sagittarius.data.NutritionGoalEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetFoodUnitToolTest {

    private val banana = FoodEntity(
        id = 5, name = "香蕉", unit = "g", weightPerUnit = 1.0,
        caloriesPer100g = 93.0, carbsPer100g = 22.0, fatPer100g = 0.2,
        proteinPer100g = 1.4, fiberPer100g = 1.2, vitaminPer100g = 8.7,
        waterPer100g = 75.0
    )

    private class FakePort(val food: FoodEntity) : DietPort {
        val units = mutableListOf<FoodUnitEntity>()
        override suspend fun ensureSeeded() = Unit
        override suspend fun findFoodByName(name: String) = food.takeIf { it.name == name }
        override suspend fun getUnits(foodId: Long) = units.filter { it.foodId == foodId }
        override suspend fun addRecord(record: DietRecordEntity): Long = 1L
        override suspend fun addFood(food: FoodEntity): FoodEntity? = food
        override suspend fun updateFood(food: FoodEntity) = Unit
        override suspend fun deleteFood(food: FoodEntity): Boolean = true
        override suspend fun recordsBetween(
            start: java.time.LocalDate,
            end: java.time.LocalDate
        ) = emptyList<DietRecordEntity>()
        override suspend fun getGoal(): NutritionGoalEntity? = null
        override suspend fun saveGoal(goal: NutritionGoalEntity) = Unit
        override suspend fun addUnit(foodId: Long, unitName: String, weightInGrams: Double): Boolean {
            units += FoodUnitEntity(foodId = foodId, unitName = unitName, weightInGrams = weightInGrams)
            return true
        }
    }

    @Test fun setsBananaUnit() = runBlocking {
        val port = FakePort(banana)
        val result = SetFoodUnitTool(port).execute(
            Json.parseToJsonElement(
                """{"food_name":"香蕉","unit_name":"根","grams_per_unit":100}"""
            ).jsonObject
        )
        assertTrue(result.ok)
        assertEquals(1, port.units.size)
        assertEquals("根", port.units.first().unitName)
        assertEquals(100.0, port.units.first().weightInGrams, 0.001)
    }

    @Test fun rejectsBaseUnitAndMissingFood() = runBlocking {
        val port = FakePort(banana)
        val base = SetFoodUnitTool(port).execute(
            Json.parseToJsonElement(
                """{"food_name":"香蕉","unit_name":"g","grams_per_unit":100}"""
            ).jsonObject
        )
        assertFalse(base.ok)
        val missing = SetFoodUnitTool(port).execute(
            Json.parseToJsonElement(
                """{"food_name":"榴莲","unit_name":"块","grams_per_unit":50}"""
            ).jsonObject
        )
        assertFalse(missing.ok)
    }
}
