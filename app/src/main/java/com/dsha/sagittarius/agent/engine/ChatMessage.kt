package com.dsha.sagittarius.agent.engine

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class ChatMessage(
    val role: String,
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
)
