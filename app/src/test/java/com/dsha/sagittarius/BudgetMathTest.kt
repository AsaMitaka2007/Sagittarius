package com.dsha.sagittarius

import com.dsha.sagittarius.data.AppStateEntity
import com.dsha.sagittarius.data.BudgetEntity
import com.dsha.sagittarius.data.BudgetMath
import com.dsha.sagittarius.data.BudgetRangeCalculator
import com.dsha.sagittarius.data.BudgetRangeType
import com.dsha.sagittarius.data.BudgetTreeMath
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetMathTest {

    private val today = LocalDate.of(2025, 6, 18)

    private val food = CategoryEntity(
        id = 1L, systemKey = "FOOD", name = "餐饮", iconKey = "food",
        color = 0, isCustom = false, sortOrder = 1
    )
    private val shopping = CategoryEntity(
        id = 3L, systemKey = "SHOPPING", name = "购物", iconKey = "shopping",
        color = 0, isCustom = false, sortOrder = 3
    )
    private val categories = mapOf(food.id to food, shopping.id to shopping)

    @Test
    fun `money parser keeps two decimal places and rejects overflow`() {
        assertEquals(1234L, Money.parseYuanToCents("12.34"))
        assertEquals(50L, Money.parseYuanToCents(".5"))
        assertEquals(120_000L, Money.parseYuanToCents("1,200"))
        assertEquals(0L, Money.parseYuanToCents("0"))
        assertNull(Money.parseYuanToCents("12.345"))
        assertNull(Money.parseYuanToCents("-1"))
        assertNull(Money.parseYuanToCents("abc"))
    }

    @Test
    fun `range calculations use monday to sunday`() {
        val week = BudgetRangeCalculator.rangeFor(BudgetRangeType.THIS_WEEK, today)
        assertEquals(LocalDate.of(2025, 6, 16), week.start)
        assertEquals(LocalDate.of(2025, 6, 22), week.end)

        val lastSeven = BudgetRangeCalculator.rangeFor(BudgetRangeType.LAST_7_DAYS, today)
        assertEquals(LocalDate.of(2025, 6, 12), lastSeven.start)
        assertEquals(today, lastSeven.end)

        val month = BudgetRangeCalculator.rangeFor(BudgetRangeType.THIS_MONTH, today)
        assertEquals(LocalDate.of(2025, 6, 1), month.start)
        assertEquals(LocalDate.of(2025, 6, 30), month.end)

        val nextMonth = BudgetRangeCalculator.rangeFor(BudgetRangeType.NEXT_MONTH, today)
        assertEquals(LocalDate.of(2025, 7, 1), nextMonth.start)
        assertEquals(LocalDate.of(2025, 7, 31), nextMonth.end)
        assertEquals(
            listOf(
                BudgetRangeType.THIS_MONTH,
                BudgetRangeType.NEXT_MONTH,
                BudgetRangeType.THIS_WEEK,
                BudgetRangeType.LAST_7_DAYS,
                BudgetRangeType.CUSTOM
            ),
            BudgetRangeType.presets
        )
    }

    @Test
    fun `snapshot uses cents and shows negative remaining on overspend`() {
        val state = AppStateEntity(
            activeRangeType = BudgetRangeType.THIS_MONTH,
            activeStartDate = LocalDate.of(2025, 6, 1),
            activeEndDate = LocalDate.of(2025, 6, 30),
            activeBudgetId = 1L,
            floatingEnabled = true
        )
        val budget = BudgetEntity(
            id = 1L,
            rangeType = BudgetRangeType.THIS_MONTH,
            startDate = state.activeStartDate,
            endDate = state.activeEndDate,
            amountCents = 100_000L
        )
        val expenses = listOf(
            ExpenseEntity(amountCents = 60_000L, date = today, categoryId = food.id),
            ExpenseEntity(amountCents = 55_000L, date = today, categoryId = shopping.id)
        )
        val snapshot = BudgetMath.createSnapshot(state, budget, expenses, categories)
        assertEquals(115_000L, snapshot.totalCents)
        assertEquals(-15_000L, snapshot.remainingCents)
        assertTrue(snapshot.isOverBudget)
        assertEquals(-0.15, BudgetMath.remainingRatio(snapshot)!!, 0.00001)
    }

    @Test
    fun `summary computes category share and daily average`() {
        val start = LocalDate.of(2025, 6, 1)
        val expenses = listOf(
            ExpenseEntity(amountCents = 3000L, date = start, categoryId = food.id),
            ExpenseEntity(amountCents = 1000L, date = start.plusDays(9), categoryId = food.id),
            ExpenseEntity(amountCents = 6000L, date = start.plusDays(9), categoryId = shopping.id)
        )
        val summary = BudgetMath.summarize(expenses, start, start.plusDays(9), categories)
        assertEquals(10_000L, summary.totalCents)
        assertEquals(3, summary.count)
        assertEquals(1_000L, summary.dailyAverageCents)
        assertEquals(6_000L, summary.largestCents)
        assertEquals(shopping, summary.categoryTotals.first().category)
        assertEquals(0.6f, summary.categoryTotals.first().share, 0.0001f)
    }

    @Test
    fun `budget tree keeps allocation view separate from total remaining`() {
        val start = LocalDate.of(2025, 6, 1)
        val end = LocalDate.of(2025, 6, 30)
        val nodes = listOf(
            com.dsha.sagittarius.data.BudgetNodeEntity(
                id = 1L, parentId = null, budgetName = "餐饮", amountCents = 40_000L,
                categoryId = food.id, sortOrder = 1, rangeType = BudgetRangeType.THIS_MONTH,
                startDate = start, endDate = end
            ),
            com.dsha.sagittarius.data.BudgetNodeEntity(
                id = 2L, parentId = 1L, budgetName = "午餐", amountCents = 15_000L,
                categoryId = food.id, sortOrder = 2, rangeType = BudgetRangeType.THIS_MONTH,
                startDate = start, endDate = end
            )
        )
        val expenses = listOf(
            ExpenseEntity(amountCents = 12_000L, date = start, categoryId = food.id)
        )
        val model = BudgetTreeMath.build(
            nodes, 100_000L, expenses,
            mapOf(food.id to "餐饮", shopping.id to "购物")
        )
        assertEquals(100_000L, model.totalCents)
        assertEquals(40_000L, model.topAllocatedCents)
        assertEquals(60_000L, model.unallocatedCents)
        assertEquals(12_000L, model.nodes.first().usedCents)
        assertEquals(0.4f, model.nodes.first().allocationShare, 0.0001f)
        assertEquals(0.3f, model.nodes.first().usedShare, 0.0001f)
    }
}
