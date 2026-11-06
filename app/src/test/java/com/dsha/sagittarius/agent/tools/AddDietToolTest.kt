package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.FoodUnitEntity
import com.dsha.sagittarius.data.NutritionGoalEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AddDietToolTest {
    private val fixed = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC)
    private val rice = FoodEntity(
        id = 7, name = "米饭", unit = "g", weightPerUnit = 1.0,
        caloriesPer100g = 116.0, carbsPer100g = 25.9, fatPer100g = 0.3,
        proteinPer100g = 2.6, fiberPer100g = 0.3, vitaminPer100g = 0.0, waterPer100g = 70.9
    )
    private val egg = FoodEntity(
        id = 8, name = "鸡蛋", unit = "g", weightPerUnit = 1.0,
        caloriesPer100g = 144.0, carbsPer100g = 1.3, fatPer100g = 8.8,
        proteinPer100g = 13.3, fiberPer100g = 0.0, vitaminPer100g = 0.5, waterPer100g = 74.0
    )

    private class FakePort(val foods: List<FoodEntity>, val units: List<FoodUnitEntity>) : DietPort {
        var record: DietRecordEntity? = null
        override suspend fun ensureSeeded() = Unit
        override suspend fun findFoodByName(name: String) = foods.firstOrNull { it.name == name }
        override suspend fun getUnits(foodId: Long) = units.filter { it.foodId == foodId }
        override suspend fun addRecord(record: DietRecordEntity): Long {
            this.record = record
            return 99L
        }
        override suspend fun addFood(food: FoodEntity): FoodEntity? = food
        override suspend fun updateFood(food: FoodEntity) = Unit
        override suspend fun deleteFood(food: FoodEntity): Boolean = true
        override suspend fun recordsBetween(start: java.time.LocalDate, end: java.time.LocalDate) = emptyList<DietRecordEntity>()
        override suspend fun getGoal(): NutritionGoalEntity? = null
        override suspend fun saveGoal(goal: NutritionGoalEntity) = Unit
    }

    @Test fun customUnitCalculatesGrams() = kotlinx.coroutines.runBlocking {
        val port = FakePort(listOf(egg), listOf(FoodUnitEntity(foodId = 8, unitName = "个", weightInGrams = 60.0)))
        val result = AddDietTool(port, fixed).execute(
            Json.parseToJsonElement("""{"food":"蛋","amount":1,"unit":"个","meal":"breakfast","date":"2026-11-03"}""").jsonObject
        )
        assertTrue(result.ok)
        assertEquals(60.0, port.record?.grams ?: 0.0, 0.001)
        assertEquals(86.4, port.record?.calories ?: 0.0, 0.001)
    }

    @Test fun missingMealIsInferredFromClock() = kotlinx.coroutines.runBlocking {
        val noon = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC)
        val port = FakePort(listOf(rice), emptyList())
        val result = AddDietTool(port, noon).execute(
            Json.parseToJsonElement("""{"food":"米饭","amount":100}""").jsonObject
        )
        assertTrue(result.ok)
        assertEquals(com.dsha.sagittarius.data.MealType.LUNCH, port.record?.mealType)
    }

    @Test fun missingFoodOrUnitFails() = kotlinx.coroutines.runBlocking {
        val port = FakePort(listOf(rice), emptyList())
        val tool = AddDietTool(port, fixed)
        assertFalse(tool.execute(Json.parseToJsonElement("""{"food":"不存在","amount":1,"meal":"lunch"}""").jsonObject).ok)
        assertFalse(tool.execute(Json.parseToJsonElement("""{"food":"米饭","amount":1,"unit":"碗","meal":"lunch"}""").jsonObject).ok)
    }
}
