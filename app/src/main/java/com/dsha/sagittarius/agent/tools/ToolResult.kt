package com.dsha.sagittarius.agent.tools

data class ToolResult(
    val ok: Boolean,
    val contentForModel: String,
    val display: String? = null
)
