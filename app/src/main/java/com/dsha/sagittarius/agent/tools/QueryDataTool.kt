package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.agent.tools.ports.QueryPort
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

class QueryDataTool(
    private val expense: ExpensePort,
    private val query: QueryPort,
    private val clock: Clock = Clock.systemDefaultZone()
) : AgentTool {
    override val name = "query_data"
    override val description = "查询账单、预算、饮食或营养数据；返回结构化 JSON 与自然语言摘要"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query_type", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { listOf("expense", "budget", "diet", "nutrition").forEach { add(JsonPrimitive(it)) } })
            })
            put("start_date", buildJsonObject { put("type", "string") })
            put("end_date", buildJsonObject { put("type", "string") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("query_type")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val type = args["query_type"]?.jsonPrimitive?.content.orEmpty()
        val today = LocalDate.now(clock)
        val start = parseDate(args["start_date"]?.jsonPrimitive?.content) ?: today.withDayOfMonth(1)
        val end = parseDate(args["end_date"]?.jsonPrimitive?.content) ?: YearMonth.from(today).atEndOfMonth()
        when (type) {
            "budget" -> {
                val summary = query.budgetSummary()
                ToolResult(true, buildJsonObject {
                    put("ok", true); put("query_type", "budget"); put("result", summary)
                }.toString(), summary)
            }
            "expense" -> {
                val list = query.expensesBetween(start, end)
                val categories = expense.categories().associateBy { it.id }
                val total = list.sumOf { it.amountCents }
                val json = buildJsonObject {
                    put("ok", true); put("query_type", "expense"); put("start", start.toString()); put("end", end.toString())
                    put("total", Money.formatCents(total)); put("count", list.size)
                    put("items", buildJsonArray {
                        list.take(50).forEach { item ->
                            add(buildJsonObject {
                                put("id", item.id)
                                put("date", item.date.toString())
                                put("amount", Money.formatCents(item.amountCents))
                                put("category", categories[item.categoryId]?.name.orEmpty())
                                put("note", item.note.orEmpty())
                            })
                        }
                    })
                }
                ToolResult(true, json.toString(), "共 ${list.size} 笔，合计 ${Money.formatCents(total)}")
            }
            "diet", "nutrition" -> {
                val list = query.dietBetween(start, end)
                val calories = list.sumOf { it.calories }
                val protein = list.sumOf { it.protein }
                val carbs = list.sumOf { it.carbs }
                val fat = list.sumOf { it.fat }
                val json = buildJsonObject {
                    put("ok", true); put("query_type", type); put("start", start.toString()); put("end", end.toString())
                    put("count", list.size); put("calories", calories); put("protein_g", protein); put("carbs_g", carbs); put("fat_g", fat)
                    put("items", buildJsonArray {
                        list.take(50).forEach { item ->
                            add(buildJsonObject {
                                put("date", item.date.toString())
                                put("food", item.foodName)
                                put("meal", item.mealType.name.lowercase())
                                put("grams", item.grams)
                                put("calories", item.calories)
                            })
                        }
                    })
                }
                ToolResult(true, json.toString(), "共 ${list.size} 条饮食，${"%.0f".format(calories)} kcal")
            }
            else -> ToolResult(false, buildJsonObject { put("ok", false); put("message", "query_type 只能是 expense/budget/diet/nutrition") }.toString())
        }
    }

    private fun parseDate(value: String?): LocalDate? =
        runCatching { value?.let(LocalDate::parse) }.getOrNull()
}
