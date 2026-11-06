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

private fun stringParam(description: String = ""): JsonObject = buildJsonObject {
    put("type", "string")
    if (description.isNotEmpty()) put("description", description)
}

class ToggleNetworkSearchTool(private val system: SystemPort) : AgentTool {
    override val name = "toggle_network_search"
    override val description = "开启或关闭对话界面的联网搜索开关"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("enable", buildJsonObject { put("type", "boolean") }) })
        put("required", buildJsonArray { add(JsonPrimitive("enable")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val enabled = args["enable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: return@withContext fail("enable 必须是布尔值")
        system.toggleNetworkSearch(enabled)
        ToolResult(true, buildJsonObject { put("ok", true); put("enabled", enabled) }.toString(), if (enabled) "联网搜索已开启" else "联网搜索已关闭")
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}

class UpdateThemeTool(private val system: SystemPort) : AgentTool {
    override val name = "update_theme"
    override val description = "切换 App 主题"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("theme", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { listOf("light", "dark", "glass").forEach { add(JsonPrimitive(it)) } }) }) })
        put("required", buildJsonArray { add(JsonPrimitive("theme")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val theme = args["theme"]?.jsonPrimitive?.content.orEmpty()
        val result = runCatching { system.updateTheme(theme) }.getOrElse { return@withContext fail(it.message ?: "切换失败") }
        ToolResult(true, buildJsonObject { put("ok", true); put("theme", result) }.toString(), "主题已切换为 $result")
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}

class UpdateDateColorTool(private val system: SystemPort) : AgentTool {
    override val name = "update_date_card_color"
    override val description = "更换顶栏日期卡片颜色"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("color_value", stringParam("如 极光绿、晚霞红、金色")) })
        put("required", buildJsonArray { add(JsonPrimitive("color_value")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val color = args["color_value"]?.jsonPrimitive?.content.orEmpty()
        val result = runCatching { system.updateDateColor(color) }.getOrElse { return@withContext fail(it.message ?: "切换失败") }
        ToolResult(true, buildJsonObject { put("ok", true); put("color", result) }.toString(), "顶栏颜色已切换为 $result")
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}

class ToggleFloatingWindowTool(private val system: SystemPort) : AgentTool {
    override val name = "toggle_floating_window"
    override val description = "开启或关闭桌面悬浮窗"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("enable", buildJsonObject { put("type", "boolean") }) })
        put("required", buildJsonArray { add(JsonPrimitive("enable")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val enabled = args["enable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: return@withContext fail("enable 必须是布尔值")
        val ok = system.toggleFloatingWindow(enabled)
        if (!ok) return@withContext fail("没有悬浮窗权限，请先在系统设置中授权")
        ToolResult(true, buildJsonObject { put("ok", true); put("enabled", enabled) }.toString(), if (enabled) "悬浮窗已开启" else "悬浮窗已关闭")
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}

class NavigateToTabTool(private val system: SystemPort) : AgentTool {
    override val name = "navigate_to_tab"
    override val description = "直接跳转到 App 的指定页面"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("tab_name", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { listOf("记一笔", "历史记账", "预算", "统计面板", "系统设置", "饮食", "饮食历史", "营养", "天气").forEach { add(JsonPrimitive(it)) } })
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("tab_name")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val tab = args["tab_name"]?.jsonPrimitive?.content.orEmpty()
        val result = runCatching { system.navigateToTab(tab) }.getOrElse { return@withContext fail(it.message ?: "跳转失败") }
        ToolResult(true, buildJsonObject { put("ok", true); put("tab", result) }.toString(), "已跳转到 $result")
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}

class ControlWeatherDebugTool(private val system: SystemPort) : AgentTool {
    override val name = "control_weather_debug"
    override val description = "调试顶栏天气特效"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("weather_type", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { listOf("sunny", "rainy", "snowy", "cloudy", "real").forEach { add(JsonPrimitive(it)) } }) })
        })
        put("required", buildJsonArray { add(JsonPrimitive("weather_type")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val type = args["weather_type"]?.jsonPrimitive?.content.orEmpty()
        val result = runCatching { system.controlWeather(type) }.getOrElse { return@withContext fail(it.message ?: "天气切换失败") }
        ToolResult(true, buildJsonObject { put("ok", true); put("weather", result) }.toString(), "天气特效：$result")
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}
