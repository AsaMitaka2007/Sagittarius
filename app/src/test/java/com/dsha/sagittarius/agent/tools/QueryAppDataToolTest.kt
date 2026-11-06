package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.agent.tools.ports.QueryPort
import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.data.MealType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class QueryAppDataToolTest {
    private class FakeExpense : ExpensePort {
        override suspend fun categories() = listOf(CategoryEntity(id = 1, name = "餐饮", iconKey = "food", color = 0, isCustom = false, sortOrder = 1))
        override suspend fun addExpense(amountCents: Long, date: LocalDate, categoryId: Long, note: String?) = 1L
        override suspend fun expensesBetween(start: LocalDate, end: LocalDate) = listOf(
            ExpenseEntity(id = 1, amountCents = 2500, date = start, categoryId = 1)
        )
        override suspend fun activeSnapshot() = ActiveBudgetSnapshot.empty()
        override suspend fun setBudget(amountCents: Long) = true
    }
    private class FakeQuery : QueryPort {
        override suspend fun budgetSummary() = "剩余 ¥100.00"
        override suspend fun expensesBetween(start: LocalDate, end: LocalDate) = emptyList<ExpenseEntity>()
        override suspend fun dietBetween(start: LocalDate, end: LocalDate) = listOf(
            DietRecordEntity(foodId = 1, foodName = "米饭", icon = "🍚", mealType = MealType.LUNCH, amount = 200.0, unit = "g", grams = 200.0, calories = 232.0, carbs = 0.0, fat = 0.0, protein = 0.0, fiber = 0.0, vitamin = 0.0, water = 0.0, date = start)
        )
    }

    @Test fun queriesBudgetAndHistories() = kotlinx.coroutines.runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-11-03T04:30:00Z"), ZoneOffset.UTC)
        val tool = QueryAppDataTool(FakeExpense(), FakeQuery(), clock)
        assertTrue(tool.execute(Json.parseToJsonElement("""{"query_type":"budget"}""").jsonObject).ok)
        assertTrue(tool.execute(Json.parseToJsonElement("""{"query_type":"history_expense","date_range":"this_month"}""").jsonObject).ok)
        assertTrue(tool.execute(Json.parseToJsonElement("""{"query_type":"history_diet","date_range":"today"}""").jsonObject).ok)
    }
}
