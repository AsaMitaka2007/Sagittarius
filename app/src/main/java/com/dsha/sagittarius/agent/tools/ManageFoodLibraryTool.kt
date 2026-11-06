package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.FoodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class ManageFoodLibraryTool(
    private val diet: DietPort
) : AgentTool {
    override val name = "manage_food_library"
    override val description = "添加、更新或删除食材库中的食物"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("add")); add(JsonPrimitive("update")); add(JsonPrimitive("delete"))
                })
            })
            put("food_name", buildJsonObject { put("type", "string") })
            put("category", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("水果蔬菜")); add(JsonPrimitive("肉禽蛋"))
                    add(JsonPrimitive("面食")); add(JsonPrimitive("零食")); add(JsonPrimitive("其他"))
                })
                put("description", "必须根据食物属性选择分类")
            })
            put("calories", buildJsonObject { put("type", "number") })
            put("protein", buildJsonObject { put("type", "number") })
            put("carbs", buildJsonObject { put("type", "number") })
            put("fat", buildJsonObject { put("type", "number") })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("action")); add(JsonPrimitive("food_name")); add(JsonPrimitive("category"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.jsonPrimitive?.content?.trim()?.lowercase().orEmpty()
        val name = args["food_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val categoryText = args["category"]?.jsonPrimitive?.content?.trim().orEmpty()
        val categoryKey = categoryKey(categoryText) ?: return@withContext fail("category 必须是水果蔬菜/肉禽蛋/面食/零食/其他")
        if (action !in setOf("add", "update", "delete")) return@withContext fail("action 只能是 add/update/delete")
        if (name.isEmpty()) return@withContext fail("缺少 food_name")
        diet.ensureSeeded()
        val existing = diet.findFoodByName(name)
        when (action) {
            "add" -> {
                if (existing != null) return@withContext fail("食材「$name」已存在，请使用 update")
                val food = FoodEntity(
                    name = name,
                    icon = "🍽️",
                    unit = "g",
                    weightPerUnit = 1.0,
                    caloriesPer100g = number(args, "calories"),
                    carbsPer100g = number(args, "carbs"),
                    fatPer100g = number(args, "fat"),
                    proteinPer100g = number(args, "protein"),
                    fiberPer100g = 0.0,
                    vitaminPer100g = 0.0,
                    waterPer100g = 0.0,
                    category = categoryKey,
                    isCustom = true
                )
                diet.addFood(food)
                return@withContext success("食材库已更新", "add", name)
            }
            "update" -> {
                if (existing == null) return@withContext fail("食材「$name」不存在")
                diet.updateFood(
                    existing.copy(
                        caloriesPer100g = number(args, "calories", existing.caloriesPer100g),
                        carbsPer100g = number(args, "carbs", existing.carbsPer100g),
                        fatPer100g = number(args, "fat", existing.fatPer100g),
                        proteinPer100g = number(args, "protein", existing.proteinPer100g),
                        category = categoryKey
                    )
                )
                return@withContext success("食材库已更新", "update", name)
            }
            else -> {
                if (existing == null) return@withContext fail("食材「$name」不存在")
                diet.deleteFood(existing)
                return@withContext success("食材已删除", "delete", name)
            }
        }
    }

    private fun categoryKey(value: String): String? = when (value) {
        "水果蔬菜" -> "FRUIT_VEG"
        "肉禽蛋" -> "MEAT_EGG"
        "面食" -> "STAPLE"
        "零食" -> "SNACK"
        "其他" -> "OTHER"
        else -> null
    }

    private fun number(args: JsonObject, key: String, default: Double = 0.0): Double =
        args[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: default

    private fun success(message: String, action: String, name: String) = ToolResult(
        ok = true,
        contentForModel = buildJsonObject {
            put("ok", true)
            put("message", message)
            put("action", action)
            put("food_name", name)
        }.toString(),
        display = "$message · $name"
    )

    private fun fail(error: String) = ToolResult(
        ok = false,
        contentForModel = buildJsonObject { put("ok", false); put("message", error) }.toString()
    )
}
