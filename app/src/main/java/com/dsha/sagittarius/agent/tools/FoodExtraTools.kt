package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.DietPort
import com.dsha.sagittarius.data.FoodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class AddFoodToLibraryTool(private val diet: DietPort) : AgentTool {
    override val name = "add_food_to_library"
    override val description = "向用户食材库添加食物，支持完整营养成分与自定义单位"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("name", buildJsonObject { put("type", "string") })
            put("category", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { listOf("水果蔬菜", "肉禽蛋", "面食", "零食", "其他").forEach { add(JsonPrimitive(it)) } }) })
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
            put("custom_units", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "object") }) })
        })
        put("required", buildJsonArray { add(JsonPrimitive("name")); add(JsonPrimitive("category")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val name = args["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (name.isEmpty()) return@withContext fail("缺少食物名称")
        val category = categoryKey(args["category"]?.jsonPrimitive?.content.orEmpty()) ?: return@withContext fail("类别无效")
        diet.ensureSeeded()
        if (diet.findFoodByName(name) != null) return@withContext fail("食材已存在")
        val food = FoodEntity(
            name = name,
            category = category,
            caloriesPer100g = num(args, "calories"),
            carbsPer100g = num(args, "carbs"),
            proteinPer100g = num(args, "protein"),
            fatPer100g = num(args, "fat"),
            fiberPer100g = num(args, "fiber"),
            waterPer100g = num(args, "water"),
            sodiumPer100g = num(args, "sodium"),
            vitaminPer100g = num(args, "vitamin"),
            vitaminAPer100g = num(args, "vitamin_a"),
            vitaminB1Per100g = num(args, "vitamin_b1"),
            vitaminB2Per100g = num(args, "vitamin_b2"),
            vitaminB3Per100g = num(args, "vitamin_b3"),
            vitaminCPer100g = num(args, "vitamin_c"),
            vitaminEPer100g = num(args, "vitamin_e"),
            isCustom = true
        )
        val saved = diet.addFood(food) ?: return@withContext fail("写入失败")
        val units = args["custom_units"] as? JsonArray ?: JsonArray(emptyList())
        units.forEach { unit ->
            val obj = unit as? JsonObject ?: return@forEach
            val unitName = obj["unit"]?.jsonPrimitive?.content?.trim().orEmpty()
            val weight = obj["weight"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            if (unitName.isNotEmpty() && weight > 0) diet.addUnit(saved.id, unitName, weight)
        }
        val missing = nutritionKeys.filter { args[it] == null }
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("id", saved.id)
                put("name", saved.name)
                put("missing_nutrition", buildJsonArray {
                    missing.forEach { add(JsonPrimitive(it)) }
                })
            }.toString(),
            "已添加食材 $name" + if (missing.isNotEmpty()) "；仍缺：${missing.joinToString("、")}" else ""
        )
    }

    private fun numberSchema(description: String) =
        buildJsonObject { put("type", "number"); put("description", description) }

    private fun num(args: JsonObject, key: String) = args[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    private fun categoryKey(value: String) = when (value) { "水果蔬菜" -> "FRUIT_VEG"; "肉禽蛋" -> "MEAT_EGG"; "面食" -> "STAPLE"; "零食" -> "SNACK"; "其他" -> "OTHER"; else -> null }
    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())

    private companion object {
        val nutritionKeys = listOf(
            "calories", "carbs", "protein", "fat", "fiber", "water", "sodium", "vitamin",
            "vitamin_a", "vitamin_b1", "vitamin_b2", "vitamin_b3", "vitamin_c", "vitamin_e"
        )
    }
}

class ManageFoodCategoryTool(private val diet: DietPort) : AgentTool {
    override val name = "manage_food_category"
    override val description = "添加或删除用户食材库分类"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("add")); add(JsonPrimitive("delete")) }) })
            put("category_name", buildJsonObject { put("type", "string") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("action")); add(JsonPrimitive("category_name")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.jsonPrimitive?.content.orEmpty()
        val name = args["category_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (name.isEmpty()) return@withContext fail("缺少分类名")
        when (action) {
            "add" -> {
                val created = diet.addFoodCategory(name) ?: return@withContext fail("分类已存在")
                ToolResult(true, buildJsonObject { put("ok", true); put("id", created.id) }.toString(), "已添加分类 $name")
            }
            "delete" -> {
                if (!diet.deleteFoodCategory(name)) return@withContext fail("分类不存在或仍有食材")
                ToolResult(true, buildJsonObject { put("ok", true) }.toString(), "已删除分类 $name")
            }
            else -> fail("action 只能是 add/delete")
        }
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}
