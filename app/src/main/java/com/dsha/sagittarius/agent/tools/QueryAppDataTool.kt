package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.agent.tools.ports.QueryPort
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

class QueryAppDataTool(
    private val expense: ExpensePort,
    private val query: QueryPort,
    private val clock: Clock = Clock.systemDefaultZone()
) : AgentTool {
    override val name = "query_app_data"
    override val description = "查询预算剩余、历史记账或历史饮食"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query_type", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("budget")); add(JsonPrimitive("history_diet")); add(JsonPrimitive("history_expense"))
                })
            })
            put("date_range", buildJsonObject {
                put("type", "string")
                put("description", "today / this_month")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("query_type")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val type = args["query_type"]?.jsonPrimitive?.content?.trim().orEmpty()
        val range = args["date_range"]?.jsonPrimitive?.content?.trim().orEmpty().ifEmpty { "today" }
        val (start, end) = parseRange(range)
        when (type) {
            "budget" -> {
                val text = query.budgetSummary()
                return@withContext ToolResult(
                    true,
                    buildJsonObject { put("ok", true); put("result", text) }.toString(),
                    display = text.take(80)
                )
            }
            "history_expense" -> {
                val list = query.expensesBetween(start, end)
                val categories = expense.categories().associateBy { it.id }
                val total = list.sumOf { it.amountCents }
                val result = buildString {
                    append("${start} 至 ${end} 共 ${list.size} 笔记账，合计 ${Money.formatCents(total)}。")
                    list.take(20).forEach { item ->
                        append("\n- ${item.date} ${categories[item.categoryId]?.name ?: "未知分类"} ${Money.formatCents(item.amountCents)}")
                        item.note?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                    }
                }
                return@withContext ToolResult(
                    true,
                    buildJsonObject { put("ok", true); put("result", result) }.toString(),
                    display = "查到 ${list.size} 笔记账"
                )
            }
            "history_diet" -> {
                val list = query.dietBetween(start, end)
                val totalKcal = list.sumOf { it.calories }
                val result = buildString {
                    append("${start} 至 ${end} 共 ${list.size} 条饮食，合计 ${"%.0f".format(totalKcal)} kcal。")
                    list.take(20).forEach { item ->
                        append("\n- ${item.date} ${item.foodName} ${"%.0f".format(item.calories)}")
                    }
                }
                return@withContext ToolResult(
                    true,
                    buildJsonObject { put("ok", true); put("result", result) }.toString(),
                    display = "查到 ${list.size} 条饮食"
                )
            }
            else -> return@withContext fail("query_type 只能是 budget/history_diet/history_expense")
        }
    }

    private fun parseRange(value: String): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(clock)
        return when (value.lowercase()) {
            "this_month", "month" -> today.withDayOfMonth(1) to YearMonth.from(today).atEndOfMonth()
            else -> today to today
        }
    }

    private fun fail(error: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", error) }.toString()
    )
}
