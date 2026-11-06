package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
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

class AddExpenseToolTest {
    private val fixed = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC)
    private val categories = listOf(
        CategoryEntity(id = 1, systemKey = "FOOD", name = "餐饮", iconKey = "food", color = 0, isCustom = false, sortOrder = 1)
    )

    private class FakePort(val categories: List<CategoryEntity>) : ExpensePort {
        var added = false
        override suspend fun categories() = categories
        override suspend fun addExpense(amountCents: Long, date: LocalDate, categoryId: Long, note: String?): Long {
            added = true
            return 12L
        }
        override suspend fun expensesBetween(start: LocalDate, end: LocalDate) = emptyList<ExpenseEntity>()
        override suspend fun activeSnapshot() = ActiveBudgetSnapshot.empty()
        override suspend fun setBudget(amountCents: Long): Boolean = true
    }

    @Test fun successWritesExpense() = kotlinx.coroutines.runBlocking {
        val port = FakePort(categories)
        val result = AddExpenseTool(port, fixed).execute(
            Json.parseToJsonElement("""{"amount":25,"category":"餐饮","note":"午饭","date":"2026-11-03"}""").jsonObject
        )
        assertTrue(result.ok)
        assertTrue(port.added)
        assertTrue(result.contentForModel.contains("\"amountCents\":2500"))
    }

    @Test fun rejectsThreeDecimalsNegativeAndFuture() = kotlinx.coroutines.runBlocking {
        val tool = AddExpenseTool(FakePort(categories), fixed)
        assertFalse(tool.execute(Json.parseToJsonElement("""{"amount":1.234,"category":"餐饮"}""").jsonObject).ok)
        assertFalse(tool.execute(Json.parseToJsonElement("""{"amount":-1,"category":"餐饮"}""").jsonObject).ok)
        assertFalse(tool.execute(Json.parseToJsonElement("""{"amount":1,"category":"餐饮","date":"2026-11-04"}""").jsonObject).ok)
    }

    @Test fun unknownCategoryFails() = kotlinx.coroutines.runBlocking {
        val result = AddExpenseTool(FakePort(categories), fixed).execute(
            Json.parseToJsonElement("""{"amount":1,"category":"不存在"}""").jsonObject
        )
        assertFalse(result.ok)
        assertTrue(result.contentForModel.contains("分类不存在"))
    }
}
