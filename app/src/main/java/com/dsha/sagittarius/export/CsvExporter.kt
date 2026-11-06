package com.dsha.sagittarius.export

import android.content.Context
import android.net.Uri
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

object CsvExporter {

    fun suggestedFileName(): String = "预算剩余_${DateText.fileStampNow()}.csv"

    suspend fun write(
        context: Context,
        uri: Uri,
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>
    ): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stream = resolver.openOutputStream(uri, "w")
            ?: error("无法打开目标文件")
        val categoryNames = categories.associate { it.id to CategoryCatalog.displayName(context, it) }
        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
            // UTF-8 BOM，保证 Excel 直接打开中文不乱码。
            writer.write("\uFEFF")
            writer.write("日期,分类,金额,备注\r\n")
            expenses.forEach { expense ->
                writer.write(
                    listOf(
                        DateText.iso(expense.date),
                        categoryNames[expense.categoryId] ?: "未知类别",
                        Money.formatPlainCents(expense.amountCents),
                        expense.note.orEmpty()
                    ).joinToString(",") { escape(it) }
                )
                writer.write("\r\n")
            }
            writer.flush()
        }
        expenses.size
    }

    private fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
