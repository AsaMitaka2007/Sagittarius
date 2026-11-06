package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ManageCategoryTool(private val port: ExpensePort) : AgentTool {
    override val name = "manage_category"
    override val description = "创建新的支出分类；创建后再用 add_expense 记账"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("name", buildJsonObject {
                put("type", "string")
                put("description", "新分类名称，不能与现有分类重名")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("name")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val name = args["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (name.isEmpty()) return@withContext fail("分类名不能为空")
        val existing = port.categories()
        if (existing.any { it.name == name }) return@withContext fail("分类已存在")
        val created = port.addCategory(name) ?: return@withContext fail("分类创建失败")
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("id", created.id)
                put("name", created.name)
            }.toString(),
            "已创建分类 ${created.name}"
        )
    }

    private fun fail(message: String) =
        ToolResult(false, buildJsonObject { put("ok", false); put("status", "error"); put("message", message) }.toString())
}
