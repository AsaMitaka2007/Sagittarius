package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.SettingsPort
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class UpdateAppSettingsTool(
    private val settings: SettingsPort
) : AgentTool {
    override val name = "update_app_settings"
    override val description = "修改主题、每日预算或营养目标"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("setting_key", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add(JsonPrimitive("theme")); add(JsonPrimitive("daily_budget")); add(JsonPrimitive("nutrition_goal"))
                })
            })
            put("value", buildJsonObject {
                put("type", "string")
                put("description", "theme: dark/light/glass；daily_budget: 元；nutrition_goal: 数字，如 150g 或 2000")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("setting_key")); add(JsonPrimitive("value")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val key = args["setting_key"]?.jsonPrimitive?.content?.trim().orEmpty()
        val value = args["value"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (key.isEmpty() || value.isEmpty()) return@withContext fail("缺少 setting_key 或 value")
        val message = runCatching {
            when (key) {
                "theme" -> "设置已成功修改为 ${settings.setTheme(value)}"
                "daily_budget" -> {
                    val cents = Money.parseYuanToCents(value)
                        ?: return@withContext fail("预算金额格式不正确")
                    if (!settings.setDailyBudget(cents)) {
                        return@withContext fail("预算设置失败，可能低于预算树已分配金额")
                    }
                    "设置已成功修改为 ${Money.formatCents(cents)}"
                }
                "nutrition_goal" -> settings.setNutritionGoal(value)
                else -> return@withContext fail("setting_key 只能是 theme/daily_budget/nutrition_goal")
            }
        }.getOrElse { return@withContext fail(it.message ?: "设置修改失败") }
        ToolResult(
            ok = true,
            contentForModel = buildJsonObject {
                put("ok", true)
                put("message", message)
            }.toString(),
            display = message
        )
    }

    private fun fail(error: String) = ToolResult(
        ok = false,
        contentForModel = buildJsonObject { put("ok", false); put("message", error) }.toString()
    )
}
