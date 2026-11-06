package com.dsha.sagittarius.agent.llm

sealed interface LlmEvent {
    data object Started : LlmEvent
    data class ReasoningDelta(val text: String) : LlmEvent
    data class ContentDelta(val text: String) : LlmEvent
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val arguments: String? = null
    ) : LlmEvent
    data class Usage(val promptTokens: Int?, val completionTokens: Int?, val totalTokens: Int? = null) : LlmEvent
    data class Completed(val finishReason: String?) : LlmEvent
    data class Failed(val error: LlmError) : LlmEvent
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportedEfforts: List<String> = listOf("off", "low", "high")
)
