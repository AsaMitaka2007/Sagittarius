package com.dsha.sagittarius.util

import com.dsha.sagittarius.data.BudgetRangeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateText {

    private val shortDate = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val dayHeader = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val month = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.CHINA)

    fun short(date: LocalDate): String = shortDate.format(date)
    fun full(date: LocalDate): String = fullDate.format(date)
    fun dayHeader(date: LocalDate): String = dayHeader.format(date)
    fun month(value: YearMonth): String = month.format(value)
    fun iso(date: LocalDate): String = iso.format(date)
    fun fileStampNow(): String = fileStamp.format(LocalDateTime.now())

    fun rangeLabel(type: BudgetRangeType, start: LocalDate, end: LocalDate): String {
        return when (type) {
            BudgetRangeType.LAST_7_DAYS -> "近 7 天 · ${short(start)}–${short(end)}"
            BudgetRangeType.THIS_WEEK -> "本周 · ${short(start)}–${short(end)}"
            BudgetRangeType.THIS_MONTH -> "本月 · ${month(YearMonth.from(start))}"
            BudgetRangeType.NEXT_MONTH -> "下个月 · ${month(YearMonth.from(start))}"
            BudgetRangeType.LAST_MONTH -> "上月 · ${month(YearMonth.from(start))}"
            BudgetRangeType.CUSTOM -> "自定义 · ${short(start)}–${short(end)}"
        }
    }

    fun relative(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> short(date)
        }
    }

    fun rangeLabelCompact(type: BudgetRangeType, start: LocalDate, end: LocalDate): String =
        when (type) {
            BudgetRangeType.LAST_7_DAYS -> "近7天"
            BudgetRangeType.THIS_WEEK -> "本周"
            BudgetRangeType.THIS_MONTH -> "本月"
            BudgetRangeType.NEXT_MONTH -> "下个月"
            BudgetRangeType.LAST_MONTH -> "上月"
            BudgetRangeType.CUSTOM -> "${short(start)}–${short(end)}"
        }
}
