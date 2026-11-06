package com.dsha.sagittarius.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class GetCnFoodNutritionTool(private val source: CnFoodSource) : AgentTool {
    override val name = "get_cn_food_nutrition"
    override val description = "按 food_id 获取本地 cn-food-mcp 食物的 25 项每100g完整营养数据"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("food_id", buildJsonObject {
                put("type", "integer")
                put("description", "search_food_cn_local 返回的食物 id")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("food_id")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val id = args["food_id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return@withContext fail("缺少有效的 food_id")
        val food = runCatching { source.getById(id) }.getOrNull()
            ?: return@withContext fail("本地营养库中不存在 id=$id")
        ToolResult(
            ok = true,
            contentForModel = buildJsonObject {
                put("ok", true)
                put("source", "本地 cn-food-mcp（离线）")
                put("per_100g", buildJsonObject {
                    put("id", food.id)
                    put("name", food.name)
                    put("category", food.category.orEmpty())
                    put("energy_kcal", food.energyKcal)
                    put("protein_g", food.proteinG)
                    put("fat_g", food.fatG)
                    put("carbohydrate_g", food.carbohydrateG)
                    put("fiber_g", food.fiberG)
                    put("water_g", food.waterG)
                    put("ash_g", food.ashG)
                    put("vitamin_a", food.vitaminA)
                    put("carotene", food.carotene)
                    put("retinol_equivalent", food.retinolEquivalent)
                    put("vitamin_b1", food.vitaminB1)
                    put("vitamin_b2", food.vitaminB2)
                    put("niacin", food.niacin)
                    put("vitamin_c", food.vitaminC)
                    put("vitamin_e", food.vitaminE)
                    put("potassium", food.potassium)
                    put("sodium", food.sodium)
                    put("calcium", food.calcium)
                    put("magnesium", food.magnesium)
                    put("iron", food.iron)
                    put("manganese", food.manganese)
                    put("zinc", food.zinc)
                    put("copper", food.copper)
                    put("phosphorus", food.phosphorus)
                    put("selenium", food.selenium)
                })
            }.toString(),
            display = "${food.name} · 25 项营养数据（本地离线库）"
        )
    }

    private fun fail(error: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", error) }.toString()
    )
}
