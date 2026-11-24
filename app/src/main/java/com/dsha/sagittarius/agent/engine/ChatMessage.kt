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
    val toolCallId: String? = null,
    /** 仅用于本轮用户消息的图片 data URL（base64），历史恢复时为空。 */
    val imageDataUrls: List<String> = emptyList()
)
