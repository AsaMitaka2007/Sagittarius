package com.dsha.sagittarius.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class DateRange(val start: LocalDate, val end: LocalDate) {
    val days: Int
        get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
}

object BudgetRangeCalculator {

    fun rangeFor(
        type: BudgetRangeType,
        today: LocalDate = LocalDate.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): DateRange {
        return when (type) {
            BudgetRangeType.LAST_7_DAYS -> DateRange(today.minusDays(6), today)
            BudgetRangeType.THIS_WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                DateRange(monday, monday.plusDays(6))
            }
            BudgetRangeType.THIS_MONTH -> {
                val first = today.withDayOfMonth(1)
                DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            BudgetRangeType.NEXT_MONTH -> {
                val first = today.plusMonths(1).withDayOfMonth(1)
                DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            BudgetRangeType.LAST_MONTH -> {
                val first = today.minusMonths(1).withDayOfMonth(1)
                DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            BudgetRangeType.CUSTOM -> {
                val start = customStart ?: today.withDayOfMonth(1)
                val end = customEnd ?: today
                if (start.isAfter(end)) DateRange(end, start) else DateRange(start, end)
            }
        }
    }
}

data class ActiveBudgetSnapshot(
    val state: AppStateEntity?,
    val budget: BudgetEntity?,
    val expenses: List<ExpenseEntity>,
    val totalCents: Long,
    val remainingCents: Long?,
    val usedRatio: Float?,
    val remainingRatio: Float?,
    val recent: List<ExpenseEntity>,
    val categories: Map<Long, CategoryEntity> = emptyMap()
) {
    val isConfigured: Boolean get() = budget != null
    val isOverBudget: Boolean get() = (remainingCents ?: 0L) < 0L
    val startDate: LocalDate? get() = state?.activeStartDate
    val endDate: LocalDate? get() = state?.activeEndDate
    val rangeType: BudgetRangeType? get() = state?.activeRangeType

    companion object {
        fun empty() = ActiveBudgetSnapshot(
            state = null,
            budget = null,
            expenses = emptyList(),
            totalCents = 0L,
            remainingCents = null,
            usedRatio = null,
            remainingRatio = null,
            recent = emptyList(),
            categories = emptyMap()
        )
    }
}

data class CategoryTotal(
    val category: CategoryEntity,
    val amountCents: Long,
    val share: Float
)

data class HistorySummary(
    val totalCents: Long,
    val count: Int,
    val dailyAverageCents: Long,
    val largestCents: Long,
    val categoryTotals: List<CategoryTotal>
)

/**
 * 整个应用唯一的计算入口。主界面、悬浮窗、历史页和小部件全部调用这里，
 * 禁止在 View / Widget 中重新实现一遍“剩余”或颜色阈值逻辑。
 */
object BudgetMath {

    fun createSnapshot(
        state: AppStateEntity,
        budget: BudgetEntity?,
        expenses: List<ExpenseEntity>,
        categories: Map<Long, CategoryEntity> = emptyMap()
    ): ActiveBudgetSnapshot {
        val total = expenses.sumOf { it.amountCents }
        val remaining = budget?.let { it.amountCents - total }
        val usedRatio = budget?.takeIf { it.amountCents > 0 }?.let {
            total.toFloat() / it.amountCents
        }
        val remainingRatio = if (remaining != null && budget != null) {
            remaining.toFloat() / budget.amountCents
        } else {
            null
        }
        return ActiveBudgetSnapshot(
            state = state,
            budget = budget,
            expenses = expenses,
            totalCents = total,
            remainingCents = remaining,
            usedRatio = usedRatio,
            remainingRatio = remainingRatio,
            recent = expenses.take(3),
            categories = categories
        )
    }

    fun summarize(
        expenses: List<ExpenseEntity>,
        periodStart: LocalDate? = null,
        periodEnd: LocalDate? = null,
        categories: Map<Long, CategoryEntity> = emptyMap()
    ): HistorySummary {
        val total = expenses.sumOf { it.amountCents }
        val start = periodStart ?: expenses.minOfOrNull { it.date }
        val end = periodEnd ?: expenses.maxOfOrNull { it.date }
        val days = if (start != null && end != null) {
            (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
        } else {
            1
        }
        val fallback = CategoryEntity(
            id = 8L,
            systemKey = "OTHER",
            name = "其他",
            iconKey = "other",
            color = 0,
            isCustom = false,
            sortOrder = 8
        )
        val categoryTotals = expenses
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val amount = list.sumOf { it.amountCents }
                CategoryTotal(
                    category = categories[categoryId] ?: fallback,
                    amountCents = amount,
                    share = if (total > 0) amount.toFloat() / total else 0f
                )
            }
            .sortedByDescending { it.amountCents }

        return HistorySummary(
            totalCents = total,
            count = expenses.size,
            dailyAverageCents = total / days,
            largestCents = expenses.maxOfOrNull { it.amountCents } ?: 0L,
            categoryTotals = categoryTotals
        )
    }

    /**
     * 剩余比例决定颜色档位：
     * >=70% 青绿；40%-70% 蓝；20%-40% 橙；0%-20% 红；<=0 深红。
     * 使用 Double 保证临界点稳定，避免 Float 误差导致颜色跳档。
     */
    fun remainingRatio(snapshot: ActiveBudgetSnapshot): Double? {
        val budget = snapshot.budget?.amountCents ?: return null
        val remaining = snapshot.remainingCents ?: return null
        if (budget <= 0L) return null
        return remaining.toDouble() / budget.toDouble()
    }

    fun isOverBudgetText(snapshot: ActiveBudgetSnapshot): Boolean =
        (snapshot.remainingCents ?: 0L) < 0

    fun absoluteCents(cents: Long): Long = abs(cents)
}
