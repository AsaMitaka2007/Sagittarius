package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.data.NutritionMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate

/** 修改历史饮食记录：可按 id 改克重/餐次/日期，并同步当前食材营养。 */
class UpdateDietRecordTool(private val repository: DietRepository) : AgentTool {
    override val name = "update_diet_record"
    override val description = "修改一条历史饮食记录（query_data 的 diet/nutrition items 里有 id）"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("record_id", buildJsonObject { put("type", "integer") })
            put("grams", buildJsonObject { put("type", "number"); put("description", "新的克重") })
            put("meal", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    listOf("breakfast", "lunch", "dinner", "snack").forEach { add(JsonPrimitive(it)) }
                })
            })
            put("date", buildJsonObject { put("type", "string"); put("description", "YYYY-MM-DD") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("record_id")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val id = args["record_id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return@withContext fail("缺少 record_id")
        val record = repository.getDietRecord(id) ?: return@withContext fail("饮食记录不存在")
        var updated = record
        args["meal"]?.jsonPrimitive?.content?.let { meal ->
            val type = MealType.fromStorage(meal.uppercase()) ?: return@withContext fail("meal 无效")
            updated = updated.copy(mealType = type)
        }
        args["date"]?.jsonPrimitive?.content?.let { date ->
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                ?: return@withContext fail("date 必须是 YYYY-MM-DD")
            updated = updated.copy(date = parsed)
        }
        val grams = args["grams"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (grams != null && grams > 0) {
            val food = repository.getFood(record.foodId)
            updated = if (food != null) {
                val n = NutritionMath.scale(food, grams)
                updated.copy(
                    grams = grams,
                    calories = n.calories,
                    carbs = n.carbs,
                    fat = n.fat,
                    protein = n.protein,
                    fiber = n.fiber,
                    vitamin = n.vitamin,
                    water = n.water,
                    sodium = n.sodium,
                    vitaminA = n.vitaminA,
                    vitaminB1 = n.vitaminB1,
                    vitaminB2 = n.vitaminB2,
                    vitaminB3 = n.vitaminB3,
                    vitaminC = n.vitaminC,
                    vitaminE = n.vitaminE
                )
            } else {
                updated.copy(grams = grams)
            }
        }
        repository.updateDietRecord(updated)
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("record_id", updated.id)
                put("food", updated.foodName)
                put("grams", updated.grams)
                put("meal", updated.mealType.name.lowercase())
                put("date", updated.date.toString())
            }.toString(),
            "已更新饮食记录：${updated.foodName} ${updated.grams}g"
        )
    }

    private fun fail(message: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", message) }.toString()
    )
}

class DeleteDietRecordTool(private val repository: DietRepository) : AgentTool {
    override val name = "delete_diet_record"
    override val description = "删除一条历史饮食记录（query_data 的 diet/nutrition items 里有 id）"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("record_id", buildJsonObject { put("type", "integer") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("record_id")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val id = args["record_id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return@withContext fail("缺少 record_id")
        val record = repository.getDietRecord(id) ?: return@withContext fail("饮食记录不存在")
        repository.deleteRecord(record)
        ToolResult(
            true,
            buildJsonObject { put("ok", true); put("record_id", id) }.toString(),
            "已删除饮食记录：${record.foodName} ${record.grams}g"
        )
    }

    private fun fail(message: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", message) }.toString()
    )
}
