package com.dsha.sagittarius.ui

import java.time.LocalDate

data class DailyPoint(
    val date: LocalDate,
    val amountCents: Long
)

data class PieSlice(
    val label: String,
    val amountCents: Long,
    val share: Float,
    val color: Int
)
