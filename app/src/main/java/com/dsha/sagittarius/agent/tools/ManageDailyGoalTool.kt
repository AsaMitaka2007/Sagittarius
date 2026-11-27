package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.data.DailyGoalMath
import com.dsha.sagittarius.data.DietRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate

/** 当日目标：AI 可新建、读取、删除、勾选完成。 */
class ManageDailyGoalTool(private val repository: DietRepository) : AgentTool {
    override val name = "manage_daily_goal"
    override val description = "管理营养页的当日目标：创建、列出、删除或完成目标"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("create")); add(JsonPrimitive("list"))
                    add(JsonPrimitive("delete")); add(JsonPrimitive("complete"))
                })
            })
            put("title", buildJsonObject { put("type", "string"); put("description", "目标名称") })
            put("nutrient", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    DailyGoalMath.keys.forEach { add(JsonPrimitive(it)) }
                })
            })
            put("amount", buildJsonObject { put("type", "number"); put("description", "数量，如 1500") })
            put("goal_id", buildJsonObject { put("type", "integer") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.jsonPrimitive?.content?.trim()?.lowercase().orEmpty()
        when (action) {
            "list" -> {
                val goals = repository.dailyGoalsList()
                ToolResult(true, buildJsonObject {
                    put("ok", true)
                    put("today", LocalDate.now().toString())
                    put("items", buildJsonArray {
                        goals.forEach { goal ->
                            add(buildJsonObject {
                                put("id", goal.id)
                                put("title", goal.title)
                                put("nutrient", goal.nutrientKey)
                                put("amount", goal.amount)
                                put("completed_today", DailyGoalMath.isCompleted(goal))
                            })
                        }
                    })
                }.toString(), "共 ${goals.size} 个当日目标")
            }
            "create" -> {
                val title = args["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                val nutrient = args["nutrient"]?.jsonPrimitive?.content?.trim().orEmpty()
                val amount = args["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                if (title.isEmpty()) return@withContext fail("缺少 title")
                if (nutrient !in DailyGoalMath.keys) return@withContext fail("nutrient 只能是 ${DailyGoalMath.keys.joinToString("/")}")
                if (amount <= 0) return@withContext fail("amount 必须大于 0")
                val goal = repository.addDailyGoal(title, nutrient, amount)
                ToolResult(true, buildJsonObject {
                    put("ok", true); put("id", goal.id); put("title", goal.title)
                }.toString(), "已创建目标：$title（${DailyGoalMath.label(nutrient, amount)}）")
            }
            "delete", "complete" -> {
                val goals = repository.dailyGoalsList()
                val id = args["goal_id"]?.jsonPrimitive?.content?.toLongOrNull()
                val title = args["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                val target = goals.firstOrNull { it.id == id }
                    ?: goals.firstOrNull { title.isNotEmpty() && it.title == title }
                    ?: return@withContext fail("没有找到目标")
                if (action == "delete") {
                    repository.deleteDailyGoal(target)
                    ToolResult(true, buildJsonObject { put("ok", true); put("id", target.id) }.toString(), "已删除目标：${target.title}")
                } else {
                    repository.setDailyGoalCompleted(target.id, true)
                    ToolResult(true, buildJsonObject { put("ok", true); put("id", target.id) }.toString(), "已完成目标：${target.title}")
                }
            }
            else -> fail("action 只能是 create/list/delete/complete")
        }
    }

    private fun fail(message: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("message", message) }.toString()
    )
}
