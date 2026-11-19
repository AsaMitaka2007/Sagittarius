package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.data.AgentMemoryCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

/** 让 AI 把用户明确要求记住的偏好写入长期记忆。 */
class RememberPreferenceTool(
    private val save: suspend (category: String, content: String) -> Boolean
) : AgentTool {

    override val name = "remember_preference"
    override val description = "记住用户的长期偏好（人设、饮食习惯、记账偏好等），后续对话会自动参考"

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "记忆分类")
                put("enum", buildJsonArray {
                    AgentMemoryCategory.all.forEach { add(JsonPrimitive(it)) }
                })
            })
            put("content", buildJsonObject {
                put("type", "string")
                put("description", "要记住的具体偏好，例如：以后记录蛋白粉自动加 500ml 水")
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("category"))
            add(JsonPrimitive("content"))
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val content = args["content"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (content.isEmpty()) return@withContext ToolResult(
            false, """{"ok":false,"error":"记忆内容不能为空"}"""
        )
        val category = AgentMemoryCategory.normalize(
            args["category"]?.jsonPrimitive?.content
        )
        val saved = runCatching { save(category, content) }.getOrDefault(false)
        if (!saved) return@withContext ToolResult(
            false, """{"ok":false,"error":"记忆保存失败，请稍后重试"}"""
        )
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("category", category)
                put("content", content)
            }.toString(),
            "已记住（$category）：$content"
        )
    }
}
