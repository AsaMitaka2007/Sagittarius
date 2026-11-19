package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

/** 为已有食材设置自定义单位换算，例如「一根香蕉 = 100g」。 */
class SetFoodUnitTool(private val diet: DietPort) : AgentTool {

    override val name = "set_food_unit"
    override val description = "为食材设置自定义单位换算，例如一根香蕉=100g、一个鸡蛋=50g"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("food_name", buildJsonObject {
                put("type", "string")
                put("description", "食材名，必须已在食材库中")
            })
            put("unit_name", buildJsonObject {
                put("type", "string")
                put("description", "自定义单位，如 根、个、杯、片、勺；不能是 g")
            })
            put("grams_per_unit", buildJsonObject {
                put("type", "number")
                put("description", "1 个该单位等于多少克，必须大于 0")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("food_name"))
            add(JsonPrimitive("unit_name"))
            add(JsonPrimitive("grams_per_unit"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val foodName = args["food_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val unitName = args["unit_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val grams = args["grams_per_unit"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (foodName.isEmpty()) return@withContext fail("缺少 food_name")
        if (unitName.isEmpty()) return@withContext fail("缺少 unit_name")
        if (unitName == "g") return@withContext fail("g 是基础单位，不需要自定义")
        if (grams == null || grams <= 0.0) return@withContext fail("grams_per_unit 必须大于 0")
        diet.ensureSeeded()
        val food = diet.findFoodByName(foodName)
            ?: return@withContext fail("食材库中没有「$foodName」，请先添加食材")
        val saved = runCatching { diet.addUnit(food.id, unitName, grams) }.getOrDefault(false)
        if (!saved) return@withContext fail("单位保存失败，请稍后重试")
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("food", food.name)
                put("unit", unitName)
                put("grams_per_unit", grams)
            }.toString(),
            "已设置：1 $unitName ${food.name} = ${trimNumber(grams)} g"
        )
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun fail(message: String): ToolResult =
        ToolResult(false, """{"ok":false,"error":"$message"}""")
}
