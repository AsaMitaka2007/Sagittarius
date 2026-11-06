package com.dsha.sagittarius.agent.tools

import kotlinx.serialization.json.JsonObject

interface AgentTool {
    val name: String
    val description: String
    val parametersSchema: JsonObject
    suspend fun execute(args: JsonObject): ToolResult
}
