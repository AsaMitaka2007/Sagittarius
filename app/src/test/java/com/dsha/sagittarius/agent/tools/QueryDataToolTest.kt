package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.agent.tools.ports.QueryPort
import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.data.MealType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class QueryDataToolTest {

    private val food = CategoryEntity(id = 1, name = "餐饮", iconKey = "food", color = 0, isCustom = false, sortOrder = 1)
    private val taxi = CategoryEntity(id = 2, name = "交通", iconKey = "transport", color = 0, isCustom = false, sortOrder = 2)

    private inner class FakeExpense : ExpensePort {
        override suspend fun categories() = listOf(food, taxi)
        override suspend fun addExpense(amountCents: Long, date: LocalDate, categoryId: Long, note: String?) = 1L
        override suspend fun expensesBetween(start: LocalDate, end: LocalDate) = listOf(
            ExpenseEntity(id = 1, amountCents = 2500, date = start, categoryId = 1, note = "午饭"),
            ExpenseEntity(id = 2, amountCents = 1200, date = start.plusDays(1), categoryId = 2, note = "打车")
        )
        override suspend fun activeSnapshot() = ActiveBudgetSnapshot.empty()
        override suspend fun setBudget(amountCents: Long) = true
    }

    private inner class FakeQuery : QueryPort {
        override suspend fun budgetSummary() = "剩余 ¥100.00"
        override suspend fun expensesBetween(start: LocalDate, end: LocalDate) = listOf(
            ExpenseEntity(id = 1, amountCents = 2500, date = start, categoryId = 1, note = "午饭"),
            ExpenseEntity(id = 2, amountCents = 1200, date = start.plusDays(1), categoryId = 2, note = "打车")
        )
        override suspend fun dietBetween(start: LocalDate, end: LocalDate) = listOf(
            DietRecordEntity(
                foodId = 1, foodName = "米饭", mealType = MealType.LUNCH,
                amount = 200.0, unit = "g", grams = 200.0, calories = 232.0,
                carbs = 50.0, fat = 1.0, protein = 5.0, fiber = 1.0,
                vitamin = 0.0, water = 100.0, sodium = 6.6,
                vitaminC = 1.0, date = start
            )
        )
    }

    private fun tool() = QueryDataTool(
        FakeExpense(),
        FakeQuery(),
        Clock.fixed(Instant.parse("2026-11-15T04:30:00Z"), ZoneOffset.UTC)
    )

    @Test
    fun `expense returns totals and category breakdown`() = runBlocking {
        val result = tool().execute(
            Json.parseToJsonElement("""{"query_type":"expense","start_date":"2026-11-01","end_date":"2026-11-15"}""").jsonObject
        )
        assertTrue(result.ok)
        val json = Json.parseToJsonElement(result.contentForModel).jsonObject
        assertEquals("2", json["count"]?.jsonPrimitive?.content)
        assertEquals("¥37.00", json["total"]?.jsonPrimitive?.content)
        assertEquals(2, json["by_category"]?.jsonArray?.size)
    }

    @Test
    fun `keyword filter narrows items`() = runBlocking {
        val result = tool().execute(
            Json.parseToJsonElement("""{"query_type":"expense","keyword":"打车"}""").jsonObject
        )
        val json = Json.parseToJsonElement(result.contentForModel).jsonObject
        assertEquals("1", json["count"]?.jsonPrimitive?.content)
        assertEquals("¥12.00", json["total"]?.jsonPrimitive?.content)
    }

    @Test
    fun `nutrition includes sodium and overview combines budget`() = runBlocking {
        val nutrition = tool().execute(
            Json.parseToJsonElement("""{"query_type":"nutrition","start_date":"2026-11-15","end_date":"2026-11-15"}""").jsonObject
        )
        val nutritionJson = Json.parseToJsonElement(nutrition.contentForModel).jsonObject
        assertEquals("6.6", nutritionJson["sodium_mg"]?.jsonPrimitive?.content)
        val overview = tool().execute(
            Json.parseToJsonElement("""{"query_type":"overview","start_date":"this_month"}""").jsonObject
        )
        val overviewJson = Json.parseToJsonElement(overview.contentForModel).jsonObject
        assertEquals("剩余 ¥100.00", overviewJson["budget"]?.jsonPrimitive?.content)
        assertTrue(overviewJson["expense"]?.jsonObject != null)
    }
}
