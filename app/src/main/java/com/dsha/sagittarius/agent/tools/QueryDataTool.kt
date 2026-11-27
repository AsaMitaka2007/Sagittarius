package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.agent.tools.ports.QueryPort
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 统一查询工具：账单、预算、饮食、营养，支持筛选、分组与汇总。
 * 返回结构化 JSON + 一句中文摘要，模型可直接据此回答。
 */
class QueryDataTool(
    private val expense: ExpensePort,
    private val query: QueryPort,
    private val clock: Clock = Clock.systemDefaultZone()
) : AgentTool {
    override val name = "query_data"
    override val description =
        "查询账单、预算、饮食或营养数据；支持日期范围、分类/关键词/餐次筛选、按日/分类/餐次分组与排行"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query_type", buildJsonObject {
                put("type", "string")
                put(
                    "enum",
                    buildJsonArray {
                        listOf("expense", "budget", "diet", "nutrition", "overview")
                            .forEach { add(JsonPrimitive(it)) }
                    }
                )
                put("description", "overview 返回预算+账单+饮食的合并概览")
            })
            put("start_date", buildJsonObject {
                put("type", "string")
                put("description", "YYYY-MM-DD；也接受 today/yesterday/this_week/this_month/last_month")
            })
            put("end_date", buildJsonObject { put("type", "string") })
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "账单分类名，精确匹配")
            })
            put("keyword", buildJsonObject {
                put("type", "string")
                put("description", "账单备注/分类或饮食食物名关键词")
            })
            put("meal", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    listOf("breakfast", "lunch", "dinner", "snack").forEach { add(JsonPrimitive(it)) }
                })
            })
            put("sort", buildJsonObject {
                put("type", "string")
                put(
                    "enum",
                    buildJsonArray {
                        listOf("date_desc", "date_asc", "amount_desc", "amount_asc")
                            .forEach { add(JsonPrimitive(it)) }
                    }
                )
            })
            put("limit", buildJsonObject {
                put("type", "integer")
                put("description", "明细条数上限，默认 50，最大 200")
            })
            put("include_items", buildJsonObject { put("type", "boolean") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("query_type")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val type = args["query_type"]?.jsonPrimitive?.content.orEmpty().lowercase()
        val today = LocalDate.now(clock)
        val startRaw = args["start_date"]?.jsonPrimitive?.content
        val endRaw = args["end_date"]?.jsonPrimitive?.content
        val start = parseDate(startRaw, today, end = false)
            ?: today.withDayOfMonth(1)
        val end = parseDate(endRaw, today, end = true)
            ?: if (startRaw?.trim()?.lowercase() == "last_month") {
                YearMonth.from(today).minusMonths(1).atEndOfMonth()
            } else if (start <= today) {
                today
            } else {
                start
            }
        val (from, to) = if (start <= end) start to end else end to start
        val keyword = args["keyword"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val categoryFilter = args["category"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val mealFilter = parseMeal(args["meal"]?.jsonPrimitive?.content)
        val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50).coerceIn(1, 200)
        val includeItems = args["include_items"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val sort = args["sort"]?.jsonPrimitive?.content?.lowercase() ?: "date_desc"

        when (type) {
            "budget" -> {
                val summary = query.budgetSummary()
                ToolResult(
                    true,
                    buildJsonObject {
                        put("ok", true)
                        put("query_type", "budget")
                        put("result", summary)
                    }.toString(),
                    summary
                )
            }
            "expense" -> {
                val categories = expense.categories().associateBy { it.id }
                val all = query.expensesBetween(from, to)
                val filtered = all.filter { item ->
                    val categoryName = categories[item.categoryId]?.name.orEmpty()
                    (categoryFilter == null || categoryName == categoryFilter) &&
                        (keyword == null ||
                            item.note.orEmpty().contains(keyword, true) ||
                            categoryName.contains(keyword, true))
                }
                val result = expenseResult(filtered, categories, from, to, sort, limit, includeItems)
                ToolResult(true, result.first.toString(), result.second)
            }
            "diet", "nutrition" -> {
                val all = query.dietBetween(from, to)
                val filtered = all.filter { item ->
                    (mealFilter == null || item.mealType == mealFilter) &&
                        (keyword == null || item.foodName.contains(keyword, true))
                }
                val result = dietResult(filtered, from, to, limit, includeItems)
                ToolResult(true, result.first.toString(), result.second)
            }
            "overview" -> {
                val categories = expense.categories().associateBy { it.id }
                val expenses = query.expensesBetween(from, to)
                val diets = query.dietBetween(from, to)
                val expensePart = expenseResult(expenses, categories, from, to, "date_desc", 0, false)
                val dietPart = dietResult(diets, from, to, 0, false)
                val json = buildJsonObject {
                    put("ok", true)
                    put("query_type", "overview")
                    put("start", from.toString())
                    put("end", to.toString())
                    put("budget", query.budgetSummary())
                    put("expense", expensePart.first)
                    put("diet", dietPart.first)
                }
                ToolResult(true, json.toString(), "${expensePart.second}；${dietPart.second}")
            }
            else -> ToolResult(
                false,
                buildJsonObject {
                    put("ok", false)
                    put("message", "query_type 只能是 expense/budget/diet/nutrition/overview")
                }.toString()
            )
        }
    }

    private fun expenseResult(
        list: List<ExpenseEntity>,
        categories: Map<Long, CategoryEntity>,
        from: LocalDate,
        to: LocalDate,
        sort: String,
        limit: Int,
        includeItems: Boolean
    ): Pair<JsonObject, String> {
        val sorted = when (sort) {
            "date_asc" -> list.sortedBy { it.date }
            "amount_desc" -> list.sortedByDescending { it.amountCents }
            "amount_asc" -> list.sortedBy { it.amountCents }
            else -> list.sortedWith(
                compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.createdAt }
            )
        }
        val total = list.sumOf { it.amountCents }
        val days = (ChronoUnit.DAYS.between(from, to).coerceAtLeast(0) + 1)
        val byCategory = list.groupBy { categories[it.categoryId]?.name ?: "未分类" }
            .map { (name, items) -> name to items.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
        val byDay = list.groupBy { it.date.toString() }
            .map { (date, items) -> date to items.sumOf { it.amountCents } }
            .sortedBy { it.first }
        val json = buildJsonObject {
            put("ok", true)
            put("query_type", "expense")
            put("start", from.toString())
            put("end", to.toString())
            put("count", list.size)
            put("total", Money.formatCents(total))
            put("average_per_item", Money.formatCents(if (list.isEmpty()) 0L else total / list.size))
            put("average_per_day", Money.formatCents(total / days))
            put("max_item", Money.formatCents(list.maxOfOrNull { it.amountCents } ?: 0L))
            put("by_category", buildJsonArray {
                byCategory.forEach { (name, amount) ->
                    add(buildJsonObject {
                        put("category", name)
                        put("amount", Money.formatCents(amount))
                        put("count", list.count { (categories[it.categoryId]?.name ?: "未分类") == name })
                    })
                }
            })
            put("by_day", buildJsonArray {
                byDay.forEach { (date, amount) ->
                    add(buildJsonObject { put("date", date); put("amount", Money.formatCents(amount)) })
                }
            })
            if (includeItems && limit > 0) {
                put("items", buildJsonArray {
                    sorted.take(limit).forEach { item ->
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
        }
        return json to "共 ${list.size} 笔，合计 ${Money.formatCents(total)}，日均 ${Money.formatCents(total / days)}"
    }

    private fun dietResult(
        list: List<DietRecordEntity>,
        from: LocalDate,
        to: LocalDate,
        limit: Int,
        includeItems: Boolean
    ): Pair<JsonObject, String> {
        val days = (ChronoUnit.DAYS.between(from, to).coerceAtLeast(0) + 1)
        val calories = list.sumOf { it.calories }
        val protein = list.sumOf { it.protein }
        val carbs = list.sumOf { it.carbs }
        val fat = list.sumOf { it.fat }
        val fiber = list.sumOf { it.fiber }
        val water = list.sumOf { it.water }
        val sodium = list.sumOf { it.sodium }
        val vitamin = list.sumOf { it.vitamin }
        val byMeal = list.groupBy { it.mealType }
            .map { (meal, items) ->
                buildJsonObject {
                    put("meal", meal.name.lowercase())
                    put("count", items.size)
                    put("calories", "%.0f".format(items.sumOf { it.calories }))
                    put("protein_g", "%.1f".format(items.sumOf { it.protein }))
                }
            }
        val byDay = list.groupBy { it.date.toString() }
            .map { (date, items) ->
                buildJsonObject {
                    put("date", date)
                    put("calories", "%.0f".format(items.sumOf { it.calories }))
                    put("protein_g", "%.1f".format(items.sumOf { it.protein }))
                }
            }
            .sortedBy { it["date"]?.jsonPrimitive?.content.orEmpty() }
        val byFood = list.groupBy { it.foodName }
            .map { (name, items) -> Triple(name, items.size, items.sumOf { it.calories }) }
            .sortedByDescending { it.third }
            .take(8)
        val json = buildJsonObject {
            put("ok", true)
            put("query_type", "nutrition")
            put("start", from.toString())
            put("end", to.toString())
            put("count", list.size)
            put("days", days)
            put("calories", "%.0f".format(calories))
            put("protein_g", "%.1f".format(protein))
            put("carbs_g", "%.1f".format(carbs))
            put("fat_g", "%.1f".format(fat))
            put("fiber_g", "%.1f".format(fiber))
            put("water_g", "%.1f".format(water))
            put("sodium_mg", "%.1f".format(sodium))
            put("vitamin_mg", "%.2f".format(vitamin))
            put("vitamin_a_ug", "%.1f".format(list.sumOf { it.vitaminA }))
            put("vitamin_b1_mg", "%.2f".format(list.sumOf { it.vitaminB1 }))
            put("vitamin_b2_mg", "%.2f".format(list.sumOf { it.vitaminB2 }))
            put("vitamin_b3_mg", "%.2f".format(list.sumOf { it.vitaminB3 }))
            put("vitamin_c_mg", "%.1f".format(list.sumOf { it.vitaminC }))
            put("vitamin_e_mg", "%.2f".format(list.sumOf { it.vitaminE }))
            put("average_calories_per_day", "%.0f".format(calories / days))
            put("by_meal", JsonArray(byMeal))
            put("by_day", JsonArray(byDay))
            put("top_foods", buildJsonArray {
                byFood.forEach { (name, count, kcal) ->
                    add(buildJsonObject {
                        put("food", name)
                        put("count", count)
                        put("calories", "%.0f".format(kcal))
                    })
                }
            })
            if (includeItems && limit > 0) {
                put("items", buildJsonArray {
                    list.sortedByDescending { it.date }.take(limit).forEach { item ->
                        add(buildJsonObject {
                            put("id", item.id)
                            put("date", item.date.toString())
                            put("food", item.foodName)
                            put("meal", item.mealType.name.lowercase())
                            put("grams", item.grams)
                            put("calories", "%.0f".format(item.calories))
                            put("protein_g", "%.1f".format(item.protein))
                        })
                    }
                })
            }
        }
        return json to "共 ${list.size} 条饮食，${"%.0f".format(calories)} kcal，日均 ${"%.0f".format(calories / days)} kcal"
    }

    private fun parseMeal(value: String?): MealType? = when (value?.lowercase()) {
        "breakfast", "早餐" -> MealType.BREAKFAST
        "lunch", "午餐" -> MealType.LUNCH
        "dinner", "晚餐" -> MealType.DINNER
        "snack", "加餐" -> MealType.SNACK
        else -> null
    }

    private fun parseDate(value: String?, today: LocalDate, end: Boolean): LocalDate? {
        if (value.isNullOrBlank()) return null
        return when (value.trim().lowercase()) {
            "today" -> today
            "yesterday" -> today.minusDays(1)
            "this_week" -> if (end) today else today.minusDays((today.dayOfWeek.value - 1).toLong())
            "this_month" -> if (end) today else today.withDayOfMonth(1)
            "last_month" -> {
                val last = YearMonth.from(today).minusMonths(1)
                if (end) last.atEndOfMonth() else last.atDay(1)
            }
            else -> runCatching { LocalDate.parse(value.trim()) }.getOrNull()
        }
    }
}
