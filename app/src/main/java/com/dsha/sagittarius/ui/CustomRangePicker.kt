package com.dsha.sagittarius.ui

import android.app.DatePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

object CustomRangePicker {

    fun show(
        context: Context,
        initialStart: LocalDate,
        initialEnd: LocalDate,
        onPicked: (start: LocalDate, end: LocalDate) -> Unit
    ) {
        val start = initialStart.coerceAtMost(initialEnd)
        val end = initialEnd.coerceAtLeast(initialStart)

        val startDialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                val selectedStart = LocalDate.of(year, month + 1, day)
                val endDialog = DatePickerDialog(
                    context,
                    { _, endYear, endMonth, endDay ->
                        val selectedEnd = LocalDate.of(endYear, endMonth + 1, endDay)
                        if (selectedStart.isAfter(selectedEnd)) {
                            onPicked(selectedEnd, selectedStart)
                        } else {
                            onPicked(selectedStart, selectedEnd)
                        }
                    },
                    end.year,
                    end.monthValue - 1,
                    end.dayOfMonth
                )
                endDialog.datePicker.minDate = selectedStart
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                endDialog.show()
            },
            start.year,
            start.monthValue - 1,
            start.dayOfMonth
        )
        startDialog.show()
    }
}
