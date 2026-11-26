package com.dsha.sagittarius.agent.llm

import com.dsha.sagittarius.agent.engine.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class LlmRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: JsonArray,
    val thinking: Boolean,
    val effort: String,
    val maxTokens: Int,
    val toolsEnabled: Boolean = true
)

object LlmJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = true }

    fun requestBody(request: LlmRequest, provider: ProviderConfig): String = buildJsonObject {
        put("model", request.model)
        put("messages", buildJsonArray {
            request.messages.forEach { add(it.toJson(provider)) }
        })
        if (request.toolsEnabled && provider.supportsTools) {
            put("tools", request.tools)
            put("tool_choice", "auto")
        }
        when (provider.thinkingStyle) {
            ThinkingStyle.DEEPSEEK_EFFORT -> {
                if (request.thinking) {
                    put("thinking", buildJsonObject { put("type", "enabled") })
                    put("reasoning_effort", request.effort)
                } else {
                    put("thinking", buildJsonObject { put("type", "disabled") })
                }
            }
            ThinkingStyle.TYPE_ONLY -> {
                val enabled = !provider.canDisableThinking || request.thinking
                put("thinking", buildJsonObject { put("type", if (enabled) "enabled" else "disabled") })
            }
            ThinkingStyle.NONE -> Unit
        }
        // 统一使用非流式，先确保标准 choices[0].message 结构可稳定解析。
        put("stream", false)
        put("max_tokens", request.maxTokens)
    }.toString()

    private fun ChatMessage.toJson(provider: ProviderConfig): JsonObject = buildJsonObject {
        put("role", role)
        if (role == "user" && imageDataUrls.isNotEmpty()) {
            // OpenAI 兼容的多模态消息：文本 + image_url
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", content.orEmpty())
                })
                imageDataUrls.forEach { dataUrl ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", dataUrl) })
                    })
                }
            })
        } else if (content != null) {
            put("content", content)
        } else if (toolCalls.isNotEmpty()) {
            // 部分 OpenAI 兼容接口不接受 tool_calls 消息的 content=null
            put("content", "")
        } else {
            put("content", JsonNull)
        }
        if (provider.echoReasoningContent &&
            (toolCalls.isNotEmpty() || !reasoningContent.isNullOrEmpty())
        ) {
            // DeepSeek 思考模式要求带 tool_calls 的 assistant 消息必须有 reasoning_content；
            // 没有推理文本时补空字符串，否则下一轮会 400。
            put("reasoning_content", reasoningContent.orEmpty())
        }
        if (toolCalls.isNotEmpty()) {
            put("tool_calls", buildJsonArray {
                toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", call.name)
                            put("arguments", call.argumentsJson)
                        })
                    })
                }
            })
        }
        if (!toolCallId.isNullOrEmpty()) put("tool_call_id", toolCallId)
    }
}
