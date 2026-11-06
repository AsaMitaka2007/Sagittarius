package com.dsha.sagittarius.ui

import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.CategoryTotal
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.data.HistorySummary
import java.time.LocalDate
import java.time.YearMonth

enum class HistoryScope { ACTIVE_RANGE, ALL, MONTH }

data class HistoryFilter(
    val scope: HistoryScope = HistoryScope.ACTIVE_RANGE,
    val categoryId: Long? = null,
    val query: String = "",
    val month: YearMonth = YearMonth.now(),
    val consoleStart: LocalDate? = null,
    val consoleEnd: LocalDate? = null
)

sealed interface HistoryItem {
    data class SummaryRow(
        val title: String,
        val summary: HistorySummary
    ) : HistoryItem

    data class CategoryRow(val totals: List<CategoryTotal>) : HistoryItem

    data class DayHeader(
        val date: LocalDate,
        val totalCents: Long
    ) : HistoryItem

    data class ExpenseRow(
        val expense: ExpenseEntity,
        val category: CategoryEntity
    ) : HistoryItem

    data object EmptyRow : HistoryItem
}

data class HistoryUiState(
    val filter: HistoryFilter,
    val items: List<HistoryItem>,
    val filteredExpenses: List<ExpenseEntity>,
    val summary: HistorySummary,
    val availableMonths: List<YearMonth>,
    val categories: List<CategoryEntity>
) {
    val isMonthScope: Boolean get() = filter.scope == HistoryScope.MONTH

    companion object {
        fun empty() = HistoryUiState(
            filter = HistoryFilter(),
            items = emptyList(),
            filteredExpenses = emptyList(),
            summary = HistorySummary(0L, 0, 0L, 0L, emptyList()),
            availableMonths = listOf(YearMonth.now()),
            categories = emptyList()
        )
    }
}
