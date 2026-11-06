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
import java.time.LocalDate

class ManageFoodLibraryToolTest {
    private class FakeDiet : DietPort {
        var food: FoodEntity? = null
        var deleted = false
        override suspend fun ensureSeeded() = Unit
        override suspend fun findFoodByName(name: String) = food?.takeIf { it.name == name }
        override suspend fun getUnits(foodId: Long) = emptyList<FoodUnitEntity>()
        override suspend fun addRecord(record: DietRecordEntity) = 1L
        override suspend fun addFood(food: FoodEntity): FoodEntity? { this.food = food; return food }
        override suspend fun updateFood(food: FoodEntity) { this.food = food }
        override suspend fun deleteFood(food: FoodEntity): Boolean { deleted = true; return true }
        override suspend fun recordsBetween(start: LocalDate, end: LocalDate) = emptyList<DietRecordEntity>()
        override suspend fun getGoal(): NutritionGoalEntity? = null
        override suspend fun saveGoal(goal: NutritionGoalEntity) = Unit
    }

    @Test fun addUpdateDelete() = kotlinx.coroutines.runBlocking {
        val port = FakeDiet()
        val tool = ManageFoodLibraryTool(port)
        val add = tool.execute(Json.parseToJsonElement("""{"action":"add","food_name":"辣条","category":"零食","calories":300,"carbs":20,"fat":15,"protein":5}""").jsonObject)
        assertTrue(add.ok)
        assertEquals("辣条", port.food?.name)
        val update = tool.execute(Json.parseToJsonElement("""{"action":"update","food_name":"辣条","category":"零食","calories":310}""").jsonObject)
        assertTrue(update.ok)
        assertEquals(310.0, port.food?.caloriesPer100g ?: 0.0, 0.001)
        val delete = tool.execute(Json.parseToJsonElement("""{"action":"delete","food_name":"辣条","category":"零食"}""").jsonObject)
        assertTrue(delete.ok)
        assertTrue(port.deleted)
    }

    @Test fun addDuplicateFails() = kotlinx.coroutines.runBlocking {
        val port = FakeDiet().apply { food = FoodEntity(name = "辣条", caloriesPer100g = 300.0, carbsPer100g = 20.0, fatPer100g = 15.0, proteinPer100g = 5.0, fiberPer100g = 0.0, vitaminPer100g = 0.0, waterPer100g = 0.0) }
        val result = ManageFoodLibraryTool(port).execute(
            Json.parseToJsonElement("""{"action":"add","food_name":"辣条","category":"零食"}""").jsonObject
        )
        assertFalse(result.ok)
    }
}
