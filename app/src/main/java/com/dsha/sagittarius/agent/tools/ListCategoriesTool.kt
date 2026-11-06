package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ListCategoriesTool(private val port: ExpensePort) : AgentTool {
    override val name = "list_categories"
    override val description = "列出当前可用的支出/预算分类，包含 id、名称、类型与父级"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val categories = runCatching { port.categories() }.getOrElse {
            return@withContext ToolResult(
                false,
                buildJsonObject { put("ok", false); put("status", "error"); put("message", "分类读取失败") }.toString()
            )
        }
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("categories", buildJsonArray {
                    categories.forEach { category ->
                        add(buildJsonObject {
                            put("id", category.id)
                            put("name", category.name)
                            put("type", category.type)
                            put("parent_id", category.parentId)
                            put("is_system", category.isSystem)
                        })
                    }
                })
            }.toString(),
            "共 ${categories.size} 个可用分类"
        )
    }
}
