package com.dsha.sagittarius.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dsha.sagittarius.data.AppStateEntity
import com.dsha.sagittarius.data.BudgetMath
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.util.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class HistoryViewModel(
    private val repository: BudgetRepository,
    private val appContext: Context
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter())

    val uiState = combine(
        repository.observeAllExpenses(),
        repository.observeState().filterNotNull(),
        repository.observeCategories(),
        filter
    ) { expenses, state, categories, currentFilter ->
        buildState(expenses, state, categories, currentFilter)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState.empty()
    )

    fun setScope(scope: HistoryScope) {
        filter.update { it.copy(scope = scope) }
    }

    fun setCategory(categoryId: Long?) {
        filter.update { it.copy(categoryId = categoryId) }
    }

    fun setQuery(value: String) {
        filter.update { it.copy(query = value.trim()) }
    }

    fun setMonth(month: YearMonth) {
        filter.update { it.copy(scope = HistoryScope.MONTH, month = month) }
    }

    fun setConsoleRange(start: LocalDate, end: LocalDate) {
        filter.update { it.copy(consoleStart = start, consoleEnd = end) }
    }

    fun addExpense(draft: ExpenseDraft) {
        viewModelScope.launch {
            repository.addExpense(draft.amountCents, draft.date, draft.categoryId, draft.note)
        }
    }

    fun updateExpense(original: ExpenseEntity, draft: ExpenseDraft) {
        viewModelScope.launch {
            repository.updateExpense(
                original.copy(
                    amountCents = draft.amountCents,
                    date = draft.date,
                    categoryId = draft.categoryId,
                    note = draft.note
                )
            )
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    fun restoreExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.addExpense(expense.amountCents, expense.date, expense.categoryId, expense.note)
        }
    }

    private fun buildState(
        allExpenses: List<ExpenseEntity>,
        state: AppStateEntity,
        categories: List<CategoryEntity>,
        currentFilter: HistoryFilter
    ): HistoryUiState {
        val query = currentFilter.query
        val categoriesById = categories.associateBy { it.id }
        val consoleStart = currentFilter.consoleStart
        val consoleEnd = currentFilter.consoleEnd
        val source = if (consoleStart != null && consoleEnd != null) {
            allExpenses.filter { !it.date.isBefore(consoleStart) && !it.date.isAfter(consoleEnd) }
        } else {
            when (currentFilter.scope) {
                HistoryScope.ACTIVE_RANGE -> allExpenses.filter {
                    !it.date.isBefore(state.activeStartDate) && !it.date.isAfter(state.activeEndDate)
                }
                HistoryScope.ALL -> allExpenses
                HistoryScope.MONTH -> allExpenses.filter {
                    YearMonth.from(it.date) == currentFilter.month
                }
            }
        }

        val filtered = source.filter { expense ->
            val category = categoriesById[expense.categoryId]
            val categoryOk = currentFilter.categoryId == null ||
                expense.categoryId == currentFilter.categoryId
            val queryOk = query.isBlank() ||
                expense.note?.contains(query, ignoreCase = true) == true ||
                category?.let {
                    CategoryCatalog.displayName(appContext, it).contains(query, ignoreCase = true)
                } == true
            categoryOk && queryOk
        }.sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.createdAt })

        val periodStart: LocalDate
        val periodEnd: LocalDate
        val baseTitle: String
        if (consoleStart != null && consoleEnd != null) {
            periodStart = consoleStart
            periodEnd = consoleEnd
            baseTitle = if (consoleStart == consoleEnd) {
                DateText.full(consoleStart)
            } else {
                "当前 · ${DateText.short(consoleStart)}–${DateText.short(consoleEnd)}"
            }
        } else {
            when (currentFilter.scope) {
                HistoryScope.ACTIVE_RANGE -> {
                    periodStart = state.activeStartDate
                    periodEnd = state.activeEndDate
                    baseTitle = "当前范围 · " + DateText.rangeLabel(
                        state.activeRangeType,
                        state.activeStartDate,
                        state.activeEndDate
                    )
                }
                HistoryScope.MONTH -> {
                    periodStart = currentFilter.month.atDay(1)
                    periodEnd = currentFilter.month.atEndOfMonth()
                    baseTitle = DateText.month(currentFilter.month)
                }
                HistoryScope.ALL -> {
                    periodStart = allExpenses.minOfOrNull { it.date } ?: LocalDate.now()
                    periodEnd = allExpenses.maxOfOrNull { it.date } ?: LocalDate.now()
                    baseTitle = "全部历史"
                }
            }
        }

        val summary = BudgetMath.summarize(filtered, periodStart, periodEnd, categoriesById)
        val selectedCategoryName = currentFilter.categoryId
            ?.let { id ->
                categoriesById[id]?.let { CategoryCatalog.displayName(appContext, it) }
            }
        val title = buildString {
            append(baseTitle)
            selectedCategoryName?.let { append(" · $it") }
            if (query.isNotBlank()) append(" · “$query”")
        }

        val items = mutableListOf<HistoryItem>()
        items += HistoryItem.SummaryRow(title, summary)
        items += HistoryItem.CategoryRow(summary.categoryTotals)
        if (filtered.isEmpty()) {
            items += HistoryItem.EmptyRow
        } else {
            filtered.groupBy { it.date }
                .toSortedMap(compareByDescending { it })
                .forEach { (date, list) ->
                    items += HistoryItem.DayHeader(date, list.sumOf { it.amountCents })
                    list.forEach { expense ->
                        items += HistoryItem.ExpenseRow(
                            expense,
                            categoriesById[expense.categoryId] ?: CategoryCatalog.fallbackEntity()
                        )
                    }
                }
        }

        val recentMonths = (0L..11L).map { YearMonth.now().minusMonths(it) }
        val months = (allExpenses.map { YearMonth.from(it.date) } + recentMonths)
            .distinct()
            .sortedDescending()

        return HistoryUiState(
            filter = currentFilter,
            items = items,
            filteredExpenses = filtered,
            summary = summary,
            availableMonths = months,
            categories = categories
        )
    }

    class Factory(
        private val repository: BudgetRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            return HistoryViewModel(repository, appContext) as T
        }
    }
}
