package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.data.CnFoodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

/** cn-food-mcp 本地只读字典的抽象，便于单测。 */
interface CnFoodSource {
    suspend fun search(query: String, limit: Int = 3): List<CnFoodEntity>
    suspend fun getById(id: Long): CnFoodEntity?
}

class SearchFoodCnLocalTool(private val source: CnFoodSource) : AgentTool {
    override val name = "search_food_cn_local"
    override val description = "在本地 cn-food-mcp 离线营养库中搜索食物，返回每100g主要营养数据"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "食物名称关键词，如 豆腐")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val query = args["query"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (query.isEmpty()) return@withContext fail("缺少食物名称")
        val foods = runCatching { source.search(query, 3) }.getOrDefault(emptyList())
        if (foods.isEmpty()) {
            return@withContext ToolResult(
                ok = false,
                contentForModel = buildJsonObject {
                    put("ok", false)
                    put(
                        "error",
                        "【系统提示】本地中文营养库未找到「$query」。若联网搜索已开启，可调用 web_search；否则请基于内置知识估算。"
                    )
                }.toString()
            )
        }
        ToolResult(
            ok = true,
            contentForModel = buildJsonObject {
                put("ok", true)
                put("source", "本地 cn-food-mcp（离线）")
                put("count", foods.size)
                put("foods", buildJsonArray {
                    foods.forEach { food ->
                        add(buildJsonObject {
                            put("id", food.id)
                            put("name", food.name)
                            put("energy_kcal", food.energyKcal)
                            put("protein_g", food.proteinG)
                            put("fat_g", food.fatG)
                            put("carbohydrate_g", food.carbohydrateG)
                            put("fiber_g", food.fiberG)
                            put("water_g", food.waterG)
                        })
                    }
                })
            }.toString(),
            display = "${foods.first().name} · ${"%.0f".format(foods.first().energyKcal)} kcal/100g（本地离线库）"
        )
    }

    private fun fail(error: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", error) }.toString()
    )
}
