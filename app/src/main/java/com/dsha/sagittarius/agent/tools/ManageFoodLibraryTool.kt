package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.FoodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ManageFoodLibraryTool(
    private val diet: DietPort
) : AgentTool {
    override val name = "manage_food_library"
    override val description = "搜索、列出、添加、更新或删除食材库中的食物，支持完整营养成分"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("search")); add(JsonPrimitive("list"))
                    add(JsonPrimitive("add")); add(JsonPrimitive("update")); add(JsonPrimitive("delete"))
                    add(JsonPrimitive("move")); add(JsonPrimitive("copy"))
                })
            })
            put("food_name", buildJsonObject {
                put("type", "string")
                put("description", "add/update/delete/move/copy 必填；search 为关键词；list 可省略")
            })
            put("new_name", buildJsonObject { put("type", "string"); put("description", "copy 时的新名称") })
            put("target_category", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("果蔬")); add(JsonPrimitive("肉禽蛋"))
                    add(JsonPrimitive("粮油调味")); add(JsonPrimitive("零食"))
                    add(JsonPrimitive("补剂")); add(JsonPrimitive("套餐"))
                })
                put("description", "move/copy 的目标分类")
            })
            put("category", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("果蔬")); add(JsonPrimitive("肉禽蛋"))
                    add(JsonPrimitive("粮油调味")); add(JsonPrimitive("零食"))
                    add(JsonPrimitive("补剂")); add(JsonPrimitive("套餐"))
                })
                put("description", "add 必填；update 不填则保留原分类")
            })
            put("limit", buildJsonObject { put("type", "integer"); put("description", "search/list 返回条数，默认 20") })
            put("calories", numberSchema("热量 kcal/100g"))
            put("carbs", numberSchema("碳水 g/100g"))
            put("protein", numberSchema("蛋白质 g/100g"))
            put("fat", numberSchema("脂肪 g/100g"))
            put("fiber", numberSchema("膳食纤维 g/100g"))
            put("water", numberSchema("水分 g/100g"))
            put("sodium", numberSchema("钠 mg/100g"))
            put("vitamin", numberSchema("维生素总量 mg/100g"))
            put("vitamin_a", numberSchema("维生素 A μg RE/100g"))
            put("vitamin_b1", numberSchema("维生素 B1 mg/100g"))
            put("vitamin_b2", numberSchema("维生素 B2 mg/100g"))
            put("vitamin_b3", numberSchema("维生素 B3/烟酸 mg/100g"))
            put("vitamin_c", numberSchema("维生素 C mg/100g"))
            put("vitamin_e", numberSchema("维生素 E mg/100g"))
        })
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.jsonPrimitive?.content?.trim()?.lowercase().orEmpty()
        val name = args["food_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val categoryText = args["category"]?.jsonPrimitive?.content?.trim().orEmpty()
        val categoryKey = categoryKey(categoryText)
        val targetKey = categoryKey(args["target_category"]?.jsonPrimitive?.content.orEmpty())
        val newName = args["new_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 20).coerceIn(1, 100)
        if (action !in setOf("search", "list", "add", "update", "delete", "move", "copy")) {
            return@withContext fail("action 只能是 search/list/add/update/delete/move/copy")
        }
        diet.ensureSeeded()
        when (action) {
            "search" -> {
                if (name.isEmpty()) return@withContext fail("搜索需要 food_name 关键词")
                val items = diet.searchFoods(name, limit)
                return@withContext searchSuccess(items, "找到 ${items.size} 个食材")
            }
            "list" -> {
                val items = diet.searchFoods("", limit)
                return@withContext searchSuccess(items, "食材库共返回 ${items.size} 个食材")
            }
            "add" -> {
                if (name.isEmpty()) return@withContext fail("缺少 food_name")
                if (categoryKey == null) {
                    return@withContext fail("category 必须是果蔬/肉禽蛋/粮油调味/零食/补剂/套餐")
                }
            }
            "update" -> if (name.isEmpty()) return@withContext fail("缺少 food_name")
            "move" -> {
                if (name.isEmpty()) return@withContext fail("缺少 food_name")
                if (targetKey == null) return@withContext fail("move 需要 target_category")
            }
            "copy" -> {
                if (name.isEmpty()) return@withContext fail("缺少 food_name")
                if (newName.isEmpty()) return@withContext fail("copy 需要 new_name")
            }
            else -> if (name.isEmpty()) return@withContext fail("缺少 food_name")
        }
        val existing = diet.findFoodByName(name)
        when (action) {
            "add" -> {
                if (existing != null) return@withContext fail("食材「$name」已存在，请使用 update")
                val food = FoodEntity(
                    name = name,
                    unit = "g",
                    weightPerUnit = 1.0,
                    caloriesPer100g = number(args, "calories"),
                    carbsPer100g = number(args, "carbs"),
                    fatPer100g = number(args, "fat"),
                    proteinPer100g = number(args, "protein"),
                    fiberPer100g = number(args, "fiber"),
                    waterPer100g = number(args, "water"),
                    sodiumPer100g = number(args, "sodium"),
                    vitaminPer100g = number(args, "vitamin"),
                    vitaminAPer100g = number(args, "vitamin_a"),
                    vitaminB1Per100g = number(args, "vitamin_b1"),
                    vitaminB2Per100g = number(args, "vitamin_b2"),
                    vitaminB3Per100g = number(args, "vitamin_b3"),
                    vitaminCPer100g = number(args, "vitamin_c"),
                    vitaminEPer100g = number(args, "vitamin_e"),
                    category = categoryKey!!,
                    isCustom = true
                )
                diet.addFood(food)
                val missing = nutritionKeys.filter { args[it] == null }
                return@withContext success("食材库已更新", "add", name, missing)
            }
            "update" -> {
                if (existing == null) return@withContext fail("食材「$name」不存在")
                diet.updateFood(
                    existing.copy(
                        caloriesPer100g = number(args, "calories", existing.caloriesPer100g),
                        carbsPer100g = number(args, "carbs", existing.carbsPer100g),
                        fatPer100g = number(args, "fat", existing.fatPer100g),
                        proteinPer100g = number(args, "protein", existing.proteinPer100g),
                        fiberPer100g = number(args, "fiber", existing.fiberPer100g),
                        waterPer100g = number(args, "water", existing.waterPer100g),
                        sodiumPer100g = number(args, "sodium", existing.sodiumPer100g),
                        vitaminPer100g = number(args, "vitamin", existing.vitaminPer100g),
                        vitaminAPer100g = number(args, "vitamin_a", existing.vitaminAPer100g),
                        vitaminB1Per100g = number(args, "vitamin_b1", existing.vitaminB1Per100g),
                        vitaminB2Per100g = number(args, "vitamin_b2", existing.vitaminB2Per100g),
                        vitaminB3Per100g = number(args, "vitamin_b3", existing.vitaminB3Per100g),
                        vitaminCPer100g = number(args, "vitamin_c", existing.vitaminCPer100g),
                        vitaminEPer100g = number(args, "vitamin_e", existing.vitaminEPer100g),
                        category = categoryKey ?: existing.category
                    )
                )
                val missing = nutritionKeys.filter { args[it] == null }
                return@withContext success("食材库已更新", "update", name, missing)
            }
            "move" -> {
                if (existing == null) return@withContext fail("食材「$name」不存在")
                if (!diet.moveFood(existing.id, targetKey!!)) {
                    return@withContext fail("移动失败")
                }
                return@withContext success("食材已移动", "move", name, emptyList())
            }
            "copy" -> {
                if (existing == null) return@withContext fail("食材「$name」不存在")
                val copied = diet.copyFood(existing.id, newName, targetKey)
                    ?: return@withContext fail("复制失败")
                return@withContext success("食材已复制为 ${copied.name}", "copy", copied.name, emptyList())
            }
            else -> {
                if (existing == null) return@withContext fail("食材「$name」不存在")
                diet.deleteFood(existing)
                return@withContext success("食材已删除", "delete", name, emptyList())
            }
        }
    }

    private fun numberSchema(description: String) =
        buildJsonObject { put("type", "number"); put("description", description) }

    private fun searchSuccess(items: List<FoodEntity>, message: String): ToolResult {
        val json = buildJsonObject {
            put("ok", true)
            put("count", items.size)
            put("items", buildJsonArray {
                items.forEach { food ->
                    add(buildJsonObject {
                        put("id", food.id)
                        put("name", food.name)
                        put("category", food.category)
                        put("calories", food.caloriesPer100g)
                        put("carbs", food.carbsPer100g)
                        put("protein", food.proteinPer100g)
                        put("fat", food.fatPer100g)
                        put("fiber", food.fiberPer100g)
                        put("water", food.waterPer100g)
                        put("sodium", food.sodiumPer100g)
                        put("vitamin", food.vitaminPer100g)
                        put("vitamin_a", food.vitaminAPer100g)
                        put("vitamin_b1", food.vitaminB1Per100g)
                        put("vitamin_b2", food.vitaminB2Per100g)
                        put("vitamin_b3", food.vitaminB3Per100g)
                        put("vitamin_c", food.vitaminCPer100g)
                        put("vitamin_e", food.vitaminEPer100g)
                    })
                }
            })
        }
        return ToolResult(true, json.toString(), message)
    }

    private fun categoryKey(value: String): String? = when (value) {
        "果蔬" -> "FRUIT_VEG"
        "肉禽蛋" -> "MEAT_EGG"
        "粮油调味" -> "STAPLE"
        "零食" -> "SNACK"
        "补剂" -> "SUPPLEMENT"
        "套餐" -> "COMBO"
        else -> null
    }

    private fun number(args: JsonObject, key: String, default: Double = 0.0): Double =
        args[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: default

    private fun success(
        message: String,
        action: String,
        name: String,
        missing: List<String>
    ) = ToolResult(
        ok = true,
        contentForModel = buildJsonObject {
            put("ok", true)
            put("message", message)
            put("action", action)
            put("food_name", name)
            put("missing_nutrition", JsonArray(missing.map { JsonPrimitive(it) }))
        }.toString(),
        display = "$message · $name" + if (missing.isNotEmpty()) "（缺 ${missing.size} 项营养）" else ""
    )

    private fun fail(error: String) = ToolResult(
        ok = false,
        contentForModel = buildJsonObject { put("ok", false); put("message", error) }.toString()
    )

    private companion object {
        val nutritionKeys = listOf(
            "calories", "carbs", "protein", "fat", "fiber", "water", "sodium", "vitamin",
            "vitamin_a", "vitamin_b1", "vitamin_b2", "vitamin_b3", "vitamin_c", "vitamin_e"
        )
    }
}
