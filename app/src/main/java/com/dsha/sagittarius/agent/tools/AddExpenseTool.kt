package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

class AddExpenseTool(
    private val port: ExpensePort,
    private val clock: Clock = Clock.systemDefaultZone()
) : AgentTool {

    override val name = "add_expense"
    override val description = "记录一笔人民币支出"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("amount", buildJsonObject {
                put("type", "number")
                put("description", "金额，单位元，必须大于 0，最多两位小数")
            })
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "分类名，须为现有分类之一；不确定时先调用 list_categories")
            })
            put("category_id", buildJsonObject {
                put("type", "integer")
                put("description", "可选；存在同名分类时指定分类 ID")
            })
            put("note", buildJsonObject {
                put("type", "string")
                put("description", "可选备注")
            })
            put("date", buildJsonObject {
                put("type", "string")
                put("description", "可选，YYYY-MM-DD，默认今天")
            })
        })
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("amount"))
            add(kotlinx.serialization.json.JsonPrimitive("category"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val amountRaw = args["amount"]?.jsonPrimitive?.content
        val cents = amountRaw?.let(Money::parseYuanToCents)
        if (cents == null || cents <= 0L) {
            return@withContext fail("金额格式不正确")
        }
        val categoryText = args["category"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (categoryText.isEmpty()) return@withContext fail("缺少分类")
        val categories = port.categories()
        val categoryId = args["category_id"]?.jsonPrimitive?.content?.toLongOrNull()
        val selectedName = categoryText.ifBlank { categories.firstOrNull { it.id == categoryId }?.name.orEmpty() }
        if (selectedName.isBlank() && categoryId == null) return@withContext fail("缺少分类")
        val category = if (categoryId != null) {
            categories.firstOrNull { it.id == categoryId }
        } else {
            val matches = categories.filter { it.name == selectedName }
            if (matches.size > 1) return@withContext fail("存在同名分类，请传 category_id")
            matches.firstOrNull()
        } ?: return@withContext fail("分类不存在，请先在设置中创建")
        val dateText = args["date"]?.jsonPrimitive?.content
        val date = try {
            if (dateText.isNullOrBlank()) LocalDate.now(clock) else LocalDate.parse(dateText)
        } catch (_: DateTimeParseException) {
            return@withContext fail("日期格式应为 YYYY-MM-DD")
        }
        val today = LocalDate.now(clock)
        if (date.isAfter(today)) return@withContext fail("日期不能晚于今天")
        if (date.isBefore(LocalDate.of(2000, 1, 1))) return@withContext fail("日期过早")
        val note = args["note"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val id = runCatching { port.addExpense(cents, date, category.id, note) }
            .getOrElse { return@withContext fail("写入失败，请稍后重试") }
        val amount = Money.formatCents(cents)
        ToolResult(
            ok = true,
            contentForModel = buildJsonObject {
                put("ok", true)
                put("id", id)
                put("amountCents", cents)
                put("amount", amount)
                put("category", category.name)
                put("date", date.toString())
            }.toString(),
            display = "${category.name} $amount · $date"
        )
    }

    private fun fail(error: String) = ToolResult(
        ok = false,
        contentForModel = buildJsonObject {
            put("ok", false)
            put("status", "error")
            put("message", error)
        }.toString()
    )
}
