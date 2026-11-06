package com.dsha.sagittarius.agent.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolRegistry {
    private val tools = linkedMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        require(tool.name.isNotBlank()) { "工具名不能为空" }
        require(!tools.containsKey(tool.name)) { "工具名重复：${tool.name}" }
        tools[tool.name] = tool
    }

    fun get(name: String): AgentTool? = tools[name]

    fun schemas(
        excludeNames: Set<String> = emptySet(),
        categories: List<String> = emptyList()
    ): JsonArray = buildJsonArray {
        val categoryHint = if (categories.isEmpty()) "" else
            "当前已有分类：[${categories.joinToString(", ")}]。"
        val categoryTools = setOf("add_expense", "update_expense", "allocate_budget_tree")
        tools.values.filterNot { it.name in excludeNames }.forEach { tool ->
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name)
                    put(
                        "description",
                        if (tool.name in categoryTools) categoryHint + tool.description else tool.description
                    )
                    put("parameters", tool.parametersSchema)
                })
            })
        }
    }

    fun all(): List<AgentTool> = tools.values.toList()
}
