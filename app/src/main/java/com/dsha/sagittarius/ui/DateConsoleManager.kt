package com.dsha.sagittarius.ui

import androidx.annotation.StringRes
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DateRange
import com.dsha.sagittarius.util.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class DateConsolePage(@StringRes val titleRes: Int) {
    ADD(R.string.tab_add),
    HISTORY(R.string.tab_history),
    BUDGET(R.string.tab_budget),
    STATS(R.string.tab_stats)
}

enum class DateConsoleGranularity { DAY, MONTH, YEAR }

data class DateConsoleState(
    val anchor: LocalDate,
    val granularity: DateConsoleGranularity,
    val locked: Boolean = false,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null
) {
    val isCustom: Boolean get() = customStart != null && customEnd != null
}

/**
 * 顶栏日期控制台的全局状态。每个 Tab 独立一份，避免切换 Tab 时互相覆盖；
 * 页面通过 StateFlow 监听并刷新自己的列表/图表/预算。
 */
object DateConsoleManager {

    private val today = LocalDate.now()
    private val flows: Map<DateConsolePage, MutableStateFlow<DateConsoleState>> =
        DateConsolePage.entries.associateWith { page ->
            MutableStateFlow(
                DateConsoleState(
                    anchor = today,
                    granularity = if (page == DateConsolePage.ADD) {
                        DateConsoleGranularity.DAY
                    } else {
                        DateConsoleGranularity.MONTH
                    }
                )
            )
        }

    fun stateOf(page: DateConsolePage): StateFlow<DateConsoleState> = flows.getValue(page).asStateFlow()

    fun setLocked(page: DateConsolePage, locked: Boolean) {
        flows.getValue(page).update { it.copy(locked = locked) }
    }

    fun toggleLocked(page: DateConsolePage) {
        flows.getValue(page).update { it.copy(locked = !it.locked) }
    }

    fun shift(page: DateConsolePage, steps: Int) {
        if (steps == 0) return
        flows.getValue(page).update { state ->
            if (state.isCustom) {
                val start = shiftDate(state.customStart!!, state.granularity, steps)
                val end = shiftDate(state.customEnd!!, state.granularity, steps)
                state.copy(anchor = start, customStart = start, customEnd = end)
            } else {
                state.copy(anchor = shiftDate(state.anchor, state.granularity, steps))
            }
        }
    }

    fun changeGranularity(page: DateConsolePage, delta: Int) {
        if (delta == 0) return
        flows.getValue(page).update { state ->
            val next = when {
                delta > 0 -> when (state.granularity) {
                    DateConsoleGranularity.DAY -> DateConsoleGranularity.MONTH
                    DateConsoleGranularity.MONTH -> DateConsoleGranularity.YEAR
                    DateConsoleGranularity.YEAR -> DateConsoleGranularity.YEAR
                }
                else -> when (state.granularity) {
                    DateConsoleGranularity.DAY -> DateConsoleGranularity.DAY
                    DateConsoleGranularity.MONTH -> DateConsoleGranularity.DAY
                    DateConsoleGranularity.YEAR -> DateConsoleGranularity.MONTH
                }
            }
            state.copy(granularity = next, customStart = null, customEnd = null)
        }
    }

    fun setCustomRange(page: DateConsolePage, start: LocalDate, end: LocalDate) {
        val range = if (start.isAfter(end)) DateRange(end, start) else DateRange(start, end)
        flows.getValue(page).update {
            it.copy(anchor = range.start, customStart = range.start, customEnd = range.end)
        }
    }

    fun setDate(page: DateConsolePage, date: LocalDate) {
        flows.getValue(page).update {
            it.copy(
                anchor = date,
                granularity = DateConsoleGranularity.DAY,
                customStart = null,
                customEnd = null
            )
        }
    }

    /** 用于预算页首次打开时对齐当前激活范围，不覆盖用户已有范围。 */
    fun syncFromRange(page: DateConsolePage, start: LocalDate, end: LocalDate) {
        val granularity = when {
            start == end -> DateConsoleGranularity.DAY
            start == start.withDayOfMonth(1) &&
                end == start.with(TemporalAdjusters.lastDayOfMonth()) -> DateConsoleGranularity.MONTH
            start == start.withDayOfYear(1) &&
                end == start.with(TemporalAdjusters.lastDayOfYear()) -> DateConsoleGranularity.YEAR
            else -> null
        }
        flows.getValue(page).update {
            if (granularity != null) {
                it.copy(
                    anchor = start,
                    granularity = granularity,
                    customStart = null,
                    customEnd = null
                )
            } else {
                it.copy(anchor = start, customStart = start, customEnd = end)
            }
        }
    }

    fun rangeOf(state: DateConsoleState): DateRange {
        if (state.isCustom) return DateRange(state.customStart!!, state.customEnd!!)
        return when (state.granularity) {
            DateConsoleGranularity.DAY -> DateRange(state.anchor, state.anchor)
            DateConsoleGranularity.MONTH -> {
                val first = state.anchor.withDayOfMonth(1)
                DateRange(first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            DateConsoleGranularity.YEAR -> {
                val first = state.anchor.withDayOfYear(1)
                DateRange(first, first.with(TemporalAdjusters.lastDayOfYear()))
            }
        }
    }

    fun shiftedRange(state: DateConsoleState, steps: Int): DateRange {
        if (steps == 0) return rangeOf(state)
        if (state.isCustom) {
            return DateRange(
                shiftDate(state.customStart!!, state.granularity, steps),
                shiftDate(state.customEnd!!, state.granularity, steps)
            )
        }
        val shifted = shiftDate(state.anchor, state.granularity, steps)
        return rangeOf(state.copy(anchor = shifted))
    }

    fun label(state: DateConsoleState): String {
        if (state.isCustom) {
            val start = state.customStart!!
            val end = state.customEnd!!
            return if (start == end) {
                DateText.short(start)
            } else {
                "${DateText.short(start)}–${DateText.short(end)}"
            }
        }
        return labelOf(rangeOf(state), state.granularity)
    }

    fun shiftedLabel(state: DateConsoleState, steps: Int): String {
        if (steps == 0) return label(state)
        if (state.isCustom) {
            val range = shiftedRange(state, steps)
            return if (range.start == range.end) {
                DateText.short(range.start)
            } else {
                "${DateText.short(range.start)}–${DateText.short(range.end)}"
            }
        }
        val shifted = shiftDate(state.anchor, state.granularity, steps)
        return labelOf(rangeOf(state.copy(anchor = shifted)), state.granularity)
    }

    private fun labelOf(range: DateRange, granularity: DateConsoleGranularity): String {
        if (range.start == range.end) return DateText.short(range.start)
        return when (granularity) {
            DateConsoleGranularity.DAY -> DateText.short(range.start)
            DateConsoleGranularity.MONTH -> DateText.month(YearMonth.from(range.start))
            DateConsoleGranularity.YEAR -> "${range.start.year}年"
        }
    }

    private fun shiftDate(date: LocalDate, granularity: DateConsoleGranularity, steps: Int): LocalDate =
        when (granularity) {
            DateConsoleGranularity.DAY -> date.plusDays(steps.toLong())
            DateConsoleGranularity.MONTH -> date.plusMonths(steps.toLong())
            DateConsoleGranularity.YEAR -> date.plusYears(steps.toLong())
        }
}
