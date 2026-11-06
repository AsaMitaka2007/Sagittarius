package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.data.NutritionMath
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
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

class AddDietTool(
    private val port: DietPort,
    private val clock: Clock = Clock.systemDefaultZone()
) : AgentTool {

    override val name = "add_diet"
    override val description = "记录一次饮食"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("food", buildJsonObject {
                put("type", "string")
                put("description", "食材名，如 米饭、鸡蛋、牛奶")
            })
            put("amount", buildJsonObject {
                put("type", "number")
                put("description", "数量，必须大于 0")
            })
            put("unit", buildJsonObject {
                put("type", "string")
                put("description", "可选单位：g、份、个、片等；缺省用食材默认单位")
            })
            put("meal", buildJsonObject {
                put("type", "string")
                put("description", "可选；未提供时按当前时间推断")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("breakfast")); add(JsonPrimitive("lunch"))
                    add(JsonPrimitive("dinner")); add(JsonPrimitive("snack"))
                })
            })
            put("date", buildJsonObject {
                put("type", "string")
                put("description", "可选，YYYY-MM-DD，默认今天")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("food")); add(JsonPrimitive("amount"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val foodText = args["food"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (foodText.isEmpty()) return@withContext fail("缺少食材名")
        val amount = args["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (amount == null || amount <= 0.0) return@withContext fail("数量必须大于 0")
        port.ensureSeeded()
        val food = port.findFoodByName(normalizeFood(foodText))
            ?: return@withContext fail("食材库中没有 $foodText，请先在饮食页添加")
        val mealText = args["meal"]?.jsonPrimitive?.content.orEmpty()
        val meal = if (mealText.isBlank()) inferMeal() else mealFrom(mealText)
        if (meal == null) return@withContext fail("餐次必须是 breakfast/lunch/dinner/snack")
        val dateText = args["date"]?.jsonPrimitive?.content
        val date = try {
            if (dateText.isNullOrBlank()) LocalDate.now(clock) else LocalDate.parse(dateText)
        } catch (_: DateTimeParseException) {
            return@withContext fail("日期格式应为 YYYY-MM-DD")
        }
        val today = LocalDate.now(clock)
        if (date.isAfter(today)) return@withContext fail("日期不能晚于今天")
        if (date.isBefore(LocalDate.of(2000, 1, 1))) return@withContext fail("日期过早")

        val requestedUnit = args["unit"]?.jsonPrimitive?.content?.trim()
        val units = port.getUnits(food.id)
        val unit = resolveUnit(food, requestedUnit, units)
            ?: return@withContext fail(
                "单位不存在，可用单位：${(listOf(food.unit) + units.map { it.unitName }).distinct().joinToString("、")}"
            )
        val record = NutritionMath.recordFrom(food, meal, amount, unit.first, unit.second, date)
        val id = runCatching { port.addRecord(record) }
            .getOrElse { return@withContext fail("写入失败，请稍后重试") }
        ToolResult(
            ok = true,
            contentForModel = buildJsonObject {
                put("ok", true)
                put("id", id)
                put("food", food.name)
                put("amount", amount)
                put("unit", unit.first)
                put("meal", meal.name.lowercase())
                put("calories", record.calories)
                put("date", date.toString())
            }.toString(),
            display = "${mealLabel(meal)} · ${food.name} ${formatAmount(amount)}${unit.first} · ${"%.0f".format(record.calories)} kcal"
        )
    }

    private fun resolveUnit(
        food: com.dsha.sagittarius.data.FoodEntity,
        requested: String?,
        units: List<com.dsha.sagittarius.data.FoodUnitEntity>
    ): Pair<String, Double>? {
        if (requested.isNullOrBlank()) {
            return if (food.unit == "g" || food.unit.isBlank()) "g" to 1.0
            else food.unit to food.weightPerUnit
        }
        if (requested == "g" || requested == "克") return "g" to 1.0
        units.firstOrNull { it.unitName == requested }?.let { return it.unitName to it.weightInGrams }
        if (requested == food.unit) return food.unit to food.weightPerUnit
        return null
    }

    private fun mealFrom(value: String): MealType? = when (value.lowercase()) {
        "breakfast", "早餐", "早饭" -> MealType.BREAKFAST
        "lunch", "午餐", "午饭" -> MealType.LUNCH
        "dinner", "晚餐", "晚饭" -> MealType.DINNER
        "snack", "加餐", "夜宵", "零食" -> MealType.SNACK
        else -> null
    }

    private fun inferMeal(): MealType {
        val hour = LocalDateTime.now(clock).hour
        return when (hour) {
            in 5..9 -> MealType.BREAKFAST
            in 10..14 -> MealType.LUNCH
            in 15..16 -> MealType.SNACK
            in 17..21 -> MealType.DINNER
            else -> MealType.SNACK
        }
    }

    private fun normalizeFood(value: String): String = when (value.trim()) {
        "白米饭" -> "米饭"
        "蛋" -> "鸡蛋"
        "西红柿", "番茄" -> "番茄"
        else -> value.trim()
    }

    private fun mealLabel(meal: MealType) = when (meal) {
        MealType.BREAKFAST -> "早餐"
        MealType.LUNCH -> "午餐"
        MealType.DINNER -> "晚餐"
        MealType.SNACK -> "加餐"
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun fail(error: String) = ToolResult(
        ok = false,
        contentForModel = buildJsonObject {
            put("ok", false)
            put("message", error)
        }.toString()
    )
}
