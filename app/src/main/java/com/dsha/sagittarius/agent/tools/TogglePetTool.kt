package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.SystemPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class TogglePetTool(private val system: SystemPort) : AgentTool {
    override val name = "toggle_pet"
    override val description = "控制对话界面左侧虚拟形象（桌宠）的显示或隐藏"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("visible", buildJsonObject { put("type", "boolean"); put("description", "true 显示，false 隐藏") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("visible")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val visible = args["visible"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: return@withContext ToolResult(false, "{\"ok\":false,\"error\":\"visible 必须是布尔值\"}")
        system.setPetVisible(visible)
        ToolResult(
            true,
            buildJsonObject { put("ok", true); put("visible", visible) }.toString(),
            if (visible) "桌宠已出现" else "桌宠已藏起来"
        )
    }
}
