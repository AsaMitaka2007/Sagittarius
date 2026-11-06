package com.dsha.sagittarius.agent.engine

import com.dsha.sagittarius.agent.debug.AgentDebugLogger
import com.dsha.sagittarius.agent.debug.NoOpAgentDebugLogger
import com.dsha.sagittarius.agent.llm.LlmClient
import com.dsha.sagittarius.agent.llm.LlmError
import com.dsha.sagittarius.agent.llm.LlmEvent
import com.dsha.sagittarius.agent.llm.LlmRequest
import com.dsha.sagittarius.agent.tools.ToolRegistry
import com.dsha.sagittarius.agent.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

class AgentEngine(
    private val llmClient: LlmClient,
    private val tools: ToolRegistry,
    private val promptProvider: SystemPromptProvider,
    private val logger: AgentDebugLogger = NoOpAgentDebugLogger,
    private val permissionGate: (suspend (String, String) -> Boolean)? = null,
    private val categoriesProvider: suspend () -> List<String> = { emptyList() }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var history: MutableList<ChatMessage> = mutableListOf()

    fun clear() {
        history.clear()
    }

    fun restoreHistory(messages: List<ChatMessage>) {
        history.clear()
        history.addAll(messages.filter { it.content?.isNotBlank() == true })
        trimHistory()
    }

    fun send(
        text: String,
        model: String,
        effort: String,
        toolsEnabled: Boolean = true,
        webSearchEnabled: Boolean = false
    ): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started)
        // 清理历史里可能存在的空 assistant，避免后续请求 400。
        history.removeAll { it.isInvalidAssistant() }
        history += ChatMessage(role = "user", content = text)
        var allowTools = toolsEnabled
        var webAllowed = webSearchEnabled
        var rounds = 0
        while (rounds < MAX_ROUNDS) {
            rounds++
            val requestHistory = history.filterNot { it.isInvalidAssistant() }
            val categoryNames = runCatching { categoriesProvider() }.getOrDefault(emptyList())
            val request = LlmRequest(
                model = model,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = promptProvider.build(webAllowed, categoryNames)
                    )
                ) + requestHistory,
                tools = tools.schemas(
                    excludeNames = if (webAllowed) emptySet() else setOf("web_search"),
                    categories = categoryNames
                ),
                thinking = effort != "off",
                effort = effort,
                maxTokens = maxTokensFor(effort),
                toolsEnabled = allowTools
            )
            var content = StringBuilder()
            var reasoning = StringBuilder()
            val calls = linkedMapOf<Int, CallBuilder>()
            var failed: LlmError? = null
            llmClient.stream(request).collect { event ->
                when (event) {
                    is LlmEvent.Started -> Unit
                    is LlmEvent.ReasoningDelta -> {
                        reasoning.append(event.text)
                        emit(AgentEvent.ReasoningDelta(event.text))
                    }
                    is LlmEvent.ContentDelta -> {
                        content.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is LlmEvent.ToolCallDelta -> {
                        val builder = calls.getOrPut(event.index) { CallBuilder() }
                        if (!event.id.isNullOrEmpty()) builder.id = event.id
                        if (!event.name.isNullOrEmpty()) builder.name = event.name
                        if (!event.arguments.isNullOrEmpty()) builder.arguments.append(event.arguments)
                    }
                    is LlmEvent.Usage -> emit(AgentEvent.Usage(event.promptTokens, event.completionTokens, event.totalTokens))
                    is LlmEvent.Completed -> Unit
                    is LlmEvent.Failed -> failed = event.error
                }
            }
            if (failed != null) {
                val retryWithoutTools = failed!!.kind == LlmError.Kind.TOOLS_UNSUPPORTED ||
                    failed!!.kind == LlmError.Kind.BAD_REQUEST
                if (retryWithoutTools && allowTools) {
                    allowTools = false
                    emit(AgentEvent.ToolsUnsupported)
                    continue
                }
                emit(AgentEvent.Failed(failed!!))
                return@flow
            }
            val toolCalls = calls.toSortedMap().values.mapNotNull { it.build() }
            val contentText = content.toString()
            if (contentText.isBlank() && toolCalls.isEmpty()) {
                emit(AgentEvent.Failed(LlmError(LlmError.Kind.BAD_REQUEST, "模型没有返回内容，请重试")))
                return@flow
            }
            val assistant = ChatMessage(
                role = "assistant",
                content = contentText.ifBlank { null },
                reasoningContent = reasoning.toString().ifEmpty { null },
                toolCalls = toolCalls
            )
            history += assistant
            if (toolCalls.isEmpty()) {
                trimHistory()
                emit(AgentEvent.Finished)
                return@flow
            }
            val duplicateGuard = mutableSetOf<String>()
            toolCalls.forEach { call ->
                val key = "${call.name}\u0000${call.argumentsJson}"
                val startedAt = System.currentTimeMillis()
                val result = if (!duplicateGuard.add(key)) {
                    ToolResult(false, """{"ok":false,"error":"重复工具调用已跳过"}""")
                } else if (call.name == "web_search" && !webAllowed) {
                    ToolResult(
                        false,
                        """{"ok":false,"error":"【系统提示】联网搜索开关未开启，请使用内置知识估算。"}"""
                    )
                } else {
                    val allowed = permissionGate?.invoke(call.name, call.argumentsJson) ?: true
                    if (!allowed) {
                        ToolResult(false, """{"ok":false,"error":"用户未批准该操作"}""")
                    } else {
                        executeTool(call)
                    }
                }
                logger.logTool(call.name, call.argumentsJson, result.contentForModel, System.currentTimeMillis() - startedAt)
                emit(AgentEvent.ToolStarted(call.name, call.argumentsJson))
                emit(AgentEvent.ToolFinished(call.name, result))
                if (call.name == "toggle_network_search" && result.ok) {
                    webAllowed = call.argumentsJson.contains("\"enable\":true") ||
                        call.argumentsJson.contains("\"enable\": true")
                }
                history += ChatMessage(
                    role = "tool",
                    content = result.contentForModel,
                    toolCallId = call.id
                )
            }
            trimHistory()
        }
        emit(
            AgentEvent.Failed(
                LlmError(LlmError.Kind.BAD_REQUEST, "工具调用次数过多，已停止")
            )
        )
    }

    private suspend fun executeTool(call: ToolCall): ToolResult {
        val tool = tools.get(call.name)
            ?: return ToolResult(false, """{"ok":false,"error":"未知工具"}""")
        val args: JsonObject = runCatching {
            if (call.argumentsJson.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(call.argumentsJson).jsonObject
        }.getOrElse {
            return ToolResult(false, """{"ok":false,"error":"参数不是合法 JSON"}""")
        }
        return runCatching {
            withTimeout(TOOL_TIMEOUT_MS) { tool.execute(args) }
        }.getOrElse { error ->
            ToolResult(
                false,
                """{"ok":false,"error":"工具执行失败：${(error as? IOException)?.message ?: "请重试"}"}"""
            )
        }
    }

    private fun ChatMessage.isInvalidAssistant(): Boolean =
        role == "assistant" && content.isNullOrBlank() && toolCalls.isEmpty()

    private fun trimHistory() {
        while (history.size > MAX_HISTORY_MESSAGES ||
            history.sumOf { (it.content?.length ?: 0) + (it.reasoningContent?.length ?: 0) } > MAX_HISTORY_CHARS
        ) {
            val nextUser = history.drop(1).indexOfFirst { it.role == "user" }
                .let { if (it < 0) -1 else it + 1 }
            if (nextUser <= 0) break
            history.subList(0, nextUser).clear()
        }
    }

    private fun maxTokensFor(effort: String): Int = when (effort) {
        "low" -> 8192
        "high" -> 16384
        "max" -> 32768
        else -> 4096
    }

    private class CallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
        fun build(): ToolCall? {
            if (name.isBlank()) return null
            return ToolCall(
                id = id.ifBlank { "call_${name}_${System.nanoTime()}" },
                name = name,
                argumentsJson = arguments.toString().ifBlank { "{}" }
            )
        }
    }

    companion object {
        private const val MAX_ROUNDS = 5
        private const val TOOL_TIMEOUT_MS = 10_000L
        private const val MAX_HISTORY_MESSAGES = 24
        private const val MAX_HISTORY_CHARS = 32_000
    }
}
