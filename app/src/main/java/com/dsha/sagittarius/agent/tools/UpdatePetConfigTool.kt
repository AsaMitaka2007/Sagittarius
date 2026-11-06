package com.dsha.sagittarius.agent.tools

import android.graphics.Color
import com.dsha.sagittarius.agent.tools.ports.SystemPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class UpdatePetConfigTool(private val system: SystemPort) : AgentTool {
    override val name = "update_pet_config"
    override val description = "修改桌宠头顶文字颜色和桌宠大小"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("text_color", buildJsonObject {
                put("type", "string")
                put("description", "主题/粉/青/黄/白，或 #RRGGBB")
            })
            put("scale_percent", buildJsonObject {
                put("type", "number")
                put("description", "50 到 200 之间的百分比")
            })
            put("locked", buildJsonObject {
                put("type", "boolean")
                put("description", "是否锁定桌宠位置")
            })
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val colorText = args["text_color"]?.jsonPrimitive?.content?.trim()
        val scale = args["scale_percent"]?.jsonPrimitive?.content?.toIntOrNull()
        val locked = args["locked"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
        if (colorText == null && scale == null && locked == null) return@withContext fail("至少提供 text_color、scale_percent 或 locked")
        var appliedColor: String? = null
        var appliedScale: Int? = null
        if (!colorText.isNullOrBlank()) {
            val color = parseColor(colorText) ?: return@withContext fail("颜色只支持 主题/粉/青/黄/白/#RRGGBB")
            system.setPetTextColor(color)
            appliedColor = colorText
        }
        if (scale != null) {
            if (scale !in 50..200) return@withContext fail("scale_percent 必须在 50-200")
            system.setPetScale(scale)
            appliedScale = scale
        }
        locked?.let { system.setPetLocked(it) }
        val result = buildJsonObject {
            put("ok", true)
            appliedColor?.let { put("text_color", it) }
            appliedScale?.let { put("scale_percent", it) }
            locked?.let { put("locked", it) }
        }
        ToolResult(true, result.toString(), "桌宠配置已更新")
    }

    private fun parseColor(value: String): Int? = when (value.lowercase()) {
        "主题", "accent", "theme" -> 0
        "粉", "粉色", "pink" -> Color.parseColor("#FF6FB5")
        "青", "青色", "cyan" -> Color.parseColor("#5EE7FF")
        "黄", "黄色", "yellow" -> Color.parseColor("#FFD166")
        "白", "白色", "white" -> Color.WHITE
        else -> runCatching { Color.parseColor(value) }.getOrNull()
    }

    private fun fail(error: String) =
        ToolResult(false, buildJsonObject { put("ok", false); put("message", error) }.toString())
}
