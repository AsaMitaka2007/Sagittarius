package com.dsha.sagittarius.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

object Money {

    private val formatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.CHINA))
    private val plainFormatter = DecimalFormat("0.00", DecimalFormatSymbols(Locale.CHINA))

    /**
     * 仅接受非负、最多两位小数的十进制输入，防止 Double 精度和 Locale 分隔符问题。
     * 返回 null 表示格式非法。0 元由业务层判断是否允许。
     */
    fun parseYuanToCents(raw: String): Long? {
        val text = raw.trim().replace("，", "").replace(",", "")
        if (text.isEmpty()) return null
        val normalized = if (text.startsWith(".")) "0$text" else text
        if (!normalized.matches(Regex("^\\d{1,9}(\\.\\d{1,2})?$"))) return null
        return try {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun formatCents(cents: Long, withSymbol: Boolean = true): String {
        val body = formatter.format(BigDecimal.valueOf(cents, 2))
        return if (withSymbol) "¥$body" else body
    }

    fun formatPlainCents(cents: Long): String =
        plainFormatter.format(BigDecimal.valueOf(cents, 2))

    fun formatSignedCents(cents: Long): String =
        if (cents < 0) "-¥${formatter.format(BigDecimal.valueOf(-cents, 2))}"
        else "¥${formatter.format(BigDecimal.valueOf(cents, 2))}"

    fun usedPercent(usedRatio: Float?): Int =
        ((usedRatio ?: 0f) * 100f).roundToInt().coerceAtLeast(0)

    fun remainingPercent(remainingRatio: Double?): Int? =
        remainingRatio?.let { (it * 100.0).roundToInt() }

    fun sharePercent(share: Float): Int =
        (share * 100f).roundToInt().coerceIn(0, 100)
}
