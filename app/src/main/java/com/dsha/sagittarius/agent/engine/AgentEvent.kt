package com.dsha.sagittarius.agent.engine

import com.dsha.sagittarius.agent.llm.LlmError
import com.dsha.sagittarius.agent.tools.ToolResult

sealed interface AgentEvent {
    data object Started : AgentEvent
    data class ReasoningDelta(val text: String) : AgentEvent
    data class TextDelta(val text: String) : AgentEvent
    data class ToolStarted(val name: String, val args: String) : AgentEvent
    data class ToolFinished(val name: String, val result: ToolResult) : AgentEvent
    data class Usage(val promptTokens: Int?, val completionTokens: Int?, val totalTokens: Int? = null) : AgentEvent
    data object ToolsUnsupported : AgentEvent
    data object Finished : AgentEvent
    data class Failed(val error: LlmError) : AgentEvent
}
