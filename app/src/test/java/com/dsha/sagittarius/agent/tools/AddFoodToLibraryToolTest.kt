package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.NutritionGoalEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFoodToLibraryToolTest {

    private class FakePort : DietPort {
        var saved: FoodEntity? = null
        override suspend fun ensureSeeded() = Unit
        override suspend fun findFoodByName(name: String): FoodEntity? = null
        override suspend fun getUnits(foodId: Long) = emptyList<com.dsha.sagittarius.data.FoodUnitEntity>()
        override suspend fun addRecord(record: DietRecordEntity): Long = 1L
        override suspend fun addFood(food: FoodEntity): FoodEntity? {
            saved = food
            return food.copy(id = 9)
        }
        override suspend fun updateFood(food: FoodEntity) = Unit
        override suspend fun deleteFood(food: FoodEntity): Boolean = true
        override suspend fun recordsBetween(
            start: java.time.LocalDate,
            end: java.time.LocalDate
        ) = emptyList<DietRecordEntity>()
        override suspend fun getGoal(): NutritionGoalEntity? = null
        override suspend fun saveGoal(goal: NutritionGoalEntity) = Unit
    }

    @Test
    fun `all nutrition fields are written and missing ones reported`() = runBlocking {
        val port = FakePort()
        val result = AddFoodToLibraryTool(port).execute(
            Json.parseToJsonElement(
                """
                {"name":"鹰嘴豆","category":"粮油调味","calories":364,"carbs":61,"protein":19,
                 "fat":6,"fiber":17,"water":12,"sodium":24,"vitamin":2,
                 "vitamin_a":3,"vitamin_b1":0.5,"vitamin_b2":0.2,"vitamin_b3":1.5,"vitamin_c":4}
                """.trimIndent()
            ).jsonObject
        )
        assertTrue(result.ok)
        val food = port.saved!!
        assertEquals(364.0, food.caloriesPer100g, 0.001)
        assertEquals(17.0, food.fiberPer100g, 0.001)
        assertEquals(12.0, food.waterPer100g, 0.001)
        assertEquals(24.0, food.sodiumPer100g, 0.001)
        assertEquals(3.0, food.vitaminAPer100g, 0.001)
        assertEquals(0.5, food.vitaminB1Per100g, 0.001)
        assertEquals(4.0, food.vitaminCPer100g, 0.001)
        val missing = Json.parseToJsonElement(result.contentForModel).jsonObject["missing_nutrition"]!!.jsonArray
        assertEquals(1, missing.size)
        assertEquals("vitamin_e", missing.first().toString().trim('"'))
    }
}
