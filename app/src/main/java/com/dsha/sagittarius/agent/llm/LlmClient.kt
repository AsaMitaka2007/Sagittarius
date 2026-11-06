package com.dsha.sagittarius.agent.llm

import com.dsha.sagittarius.agent.debug.AgentDebugLogger
import com.dsha.sagittarius.agent.debug.NoOpAgentDebugLogger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

interface LlmClient {
    fun stream(request: LlmRequest): Flow<LlmEvent>
    suspend fun fetchModels(): List<ModelInfo>
    suspend fun validateKey() { fetchModels() }
}

class OkHttpLlmClient(
    private val baseUrl: String,
    private val apiKeyProvider: suspend () -> String?,
    private val logger: AgentDebugLogger = NoOpAgentDebugLogger,
    private val provider: ProviderConfig = ProviderRegistry.get("deepseek")!!
) : LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun stream(request: LlmRequest): Flow<LlmEvent> = channelFlow {
        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            send(LlmEvent.Failed(LlmError(LlmError.Kind.AUTH, "HTTP 401：鉴权失败（未配置 Key）")))
            close()
            return@channelFlow
        }
        withContext(Dispatchers.IO) {
            var lastError: LlmError? = null
            for (attempt in 0..2) {
                try {
                    val body = LlmJson.requestBody(request, provider)
                    logger.logRequest("${baseUrl.trimEnd('/')}/chat/completions", body)
                    val call = client.newCall(
                        Request.Builder()
                            .url("${baseUrl.trimEnd('/')}/chat/completions")
                            .addHeader("Authorization", "Bearer $key")
                            .addHeader("Content-Type", "application/json")
                            .post(body.toRequestBody("application/json".toMediaType()))
                            .build()
                    )
                    currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                    call.execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        logger.logResponse(raw)
                        if (!response.isSuccessful) {
                            logger.logHttpError(response.code, response.header("x-request-id"), raw)
                            val error = mapHttpError(response.code, response.header("x-request-id"), raw)
                            if (error.retryable && attempt < 2) {
                                lastError = error
                                delay(600L * (1 shl attempt) + (0..200).random())
                                return@use
                            }
                            send(LlmEvent.Failed(error))
                            close()
                            return@withContext
                        }
                        if (raw.isBlank()) {
                            send(LlmEvent.Failed(LlmError(LlmError.Kind.PARSE, "响应为空：服务端没有返回任何 JSON")))
                            close()
                            return@withContext
                        }
                        val parsed = runCatching { parseNonStream(raw, request.model) }.getOrElse { error ->
                            logger.logError(error)
                            send(
                                LlmEvent.Failed(
                                    LlmError(
                                        LlmError.Kind.PARSE,
                                        "JSON 解析错误：${error.message ?: error.javaClass.simpleName}\n原始响应：${raw.take(800)}"
                                    )
                                )
                            )
                            close()
                            return@withContext
                        }
                        send(LlmEvent.Started)
                        if (parsed.reasoning.isNotBlank()) {
                            send(LlmEvent.ReasoningDelta(parsed.reasoning))
                        }
                        if (parsed.content.isNotBlank()) {
                            send(LlmEvent.ContentDelta(parsed.content))
                        } else if (parsed.reasoning.isNotBlank()) {
                            // 推理模型 content 可能为空，严格按需求用 reasoning_content 兜底展示。
                            send(LlmEvent.ContentDelta(parsed.reasoning))
                        }
                        parsed.toolCalls.forEachIndexed { index, callInfo ->
                            send(
                                LlmEvent.ToolCallDelta(
                                    index = index,
                                    id = callInfo.id,
                                    name = callInfo.name,
                                    arguments = callInfo.arguments
                                )
                            )
                        }
                        parsed.usage?.let { send(it) }
                        if (parsed.content.isBlank() && parsed.reasoning.isBlank() && parsed.toolCalls.isEmpty()) {
                            send(
                                LlmEvent.Failed(
                                    LlmError(
                                        LlmError.Kind.PARSE,
                                        "响应 JSON 无有效内容：${raw.take(800)}"
                                    )
                                )
                            )
                        } else {
                            send(LlmEvent.Completed(parsed.finishReason))
                        }
                        close()
                        return@withContext
                    }
                } catch (io: IOException) {
                    logger.logError(io)
                    lastError = LlmError(LlmError.Kind.NETWORK, "网络连接失败，请检查网络或代理", true)
                    if (attempt < 2) delay(600L * (1 shl attempt))
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    logger.logError(t)
                    send(LlmEvent.Failed(LlmError(LlmError.Kind.UNKNOWN, "请求异常：${t.message ?: t.javaClass.simpleName}")))
                    close()
                    return@withContext
                }
            }
            send(LlmEvent.Failed(lastError ?: LlmError(LlmError.Kind.NETWORK, "网络连接失败")))
            close()
        }
    }

    private data class ParsedResponse(
        val content: String,
        val reasoning: String,
        val toolCalls: List<ParsedToolCall>,
        val finishReason: String?,
        val usage: LlmEvent.Usage?
    )

    private data class ParsedToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )

    private fun parseNonStream(raw: String, model: String): ParsedResponse {
        val root = LlmJson.json.parseToJsonElement(raw).jsonObject
        (root["error"] as? JsonObject)?.let { err ->
            throw IllegalStateException(err["message"].stringOrNull() ?: "服务端返回 error")
        }
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw IllegalStateException("缺少 choices[0]")
        val message = (choice["message"] as? JsonObject)
            ?: (choice["delta"] as? JsonObject)
            ?: throw IllegalStateException("缺少 choices[0].message")
        val content = message["content"].stringOrNull().orEmpty()
        val reasoning = message["reasoning_content"].stringOrNull().orEmpty()
        val toolCalls = mutableListOf<ParsedToolCall>()
        (message["tool_calls"] as? JsonArray)?.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: return@forEachIndexed
            val fn = obj["function"] as? JsonObject ?: return@forEachIndexed
            toolCalls += ParsedToolCall(
                id = obj["id"].stringOrNull() ?: "call_${index}_${System.nanoTime()}",
                name = fn["name"].stringOrNull().orEmpty(),
                arguments = fn["arguments"].stringOrNull() ?: "{}"
            )
        }
        val usageObj = root["usage"] as? JsonObject
        return ParsedResponse(
            content = content,
            reasoning = reasoning,
            toolCalls = toolCalls.filter { it.name.isNotBlank() },
            finishReason = choice["finish_reason"].stringOrNull(),
            usage = usageObj?.let {
                LlmEvent.Usage(
                    it["prompt_tokens"].stringOrNull()?.toIntOrNull(),
                    it["completion_tokens"].stringOrNull()?.toIntOrNull(),
                    it["total_tokens"].stringOrNull()?.toIntOrNull()
                )
            }
        )
    }

    override suspend fun fetchModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider() ?: throw IllegalStateException("未配置 Key")
        val response = runCatching {
            client.newCall(
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .addHeader("Authorization", "Bearer $key")
                    .get()
                    .build()
            ).execute()
        }.getOrElse { throw LlmException(LlmError(LlmError.Kind.NETWORK, "网络连接失败，请检查网络或代理")) }
        response.use {
            if (!it.isSuccessful) {
                val body = it.body?.string().orEmpty()
                logger.logHttpError(it.code, it.header("x-request-id"), body)
                throw LlmException(mapHttpError(it.code, it.header("x-request-id"), body))
            }
            val root = LlmJson.json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
            val data = root["data"] as? JsonArray ?: JsonArray(emptyList())
            data.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                ModelInfo(
                    id = id,
                    displayName = obj["name"]?.jsonPrimitive?.content ?: id,
                    contextWindow = obj["context_window"]?.jsonPrimitive?.content?.toIntOrNull(),
                    maxOutputTokens = obj["max_output_tokens"]?.jsonPrimitive?.content?.toIntOrNull(),
                    supportedEfforts = (obj["effort"] as? JsonObject)?.get("supported_levels")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.content } ?: listOf("off", "low", "high")
                )
            }.ifEmpty {
                val efforts = when (provider.thinkingStyle) {
                    ThinkingStyle.DEEPSEEK_EFFORT -> listOf("off", "low", "high")
                    ThinkingStyle.TYPE_ONLY -> if (provider.canDisableThinking) listOf("off", "high") else listOf("high")
                    ThinkingStyle.NONE -> listOf("off")
                }
                provider.fallbackModels.map { ModelInfo(it, it, supportedEfforts = efforts) }
            }
        }
    }

    override suspend fun validateKey() = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()
            ?: throw LlmException(LlmError(LlmError.Kind.AUTH, "Key 无效或已失效"))
        if (provider.validateWithModelsEndpoint) {
            try {
                fetchModels()
                return@withContext
            } catch (e: LlmException) {
                if (e.error.kind == LlmError.Kind.AUTH || e.error.kind == LlmError.Kind.NETWORK) throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // /models 不可用时，继续用最小聊天请求验 Key
            }
        }
        val body = buildJsonObject {
            put("model", provider.defaultModel)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "hi")
                })
            })
            put("max_tokens", 64)
            put("stream", false)
        }.toString()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val response = runCatching { client.newCall(request).execute() }
            .getOrElse {
                throw LlmException(LlmError(LlmError.Kind.NETWORK, "网络连接失败，请检查网络或代理"))
            }
        response.use {
            if (!it.isSuccessful) {
                val errorBody = it.body?.string().orEmpty()
                logger.logHttpError(it.code, it.header("x-request-id"), errorBody)
                throw LlmException(mapHttpError(it.code, it.header("x-request-id"), errorBody))
            }
        }
    }

    private fun JsonElement?.stringOrNull(): String? =
        if (this == null || this is JsonNull) null
        else (this as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

    private fun mapHttpError(code: Int, requestId: String?, body: String = ""): LlmError = when (code) {
        401 -> LlmError(LlmError.Kind.AUTH, "HTTP 401：鉴权失败（Key 无效或已失效）", false, requestId)
        402 -> LlmError(LlmError.Kind.BALANCE, "HTTP 402：账户余额不足，请前往服务商平台充值", false, requestId)
        429 -> LlmError(LlmError.Kind.RATE_LIMIT, "HTTP 429：请求过于频繁，请稍后重试", true, requestId)
        400, 422 -> {
            val text = body.lowercase()
            val toolUnsupported = (text.contains("tool") || text.contains("function")) &&
                (text.contains("not support") || text.contains("unsupported") ||
                    text.contains("does not support") || text.contains("不支持"))
            if (toolUnsupported) {
                LlmError(LlmError.Kind.TOOLS_UNSUPPORTED, "该模型不支持工具调用，已降级为纯聊天", false, requestId)
            } else {
                val detail = body.replace(Regex("\\s+"), " ").trim().take(180)
                LlmError(
                    LlmError.Kind.BAD_REQUEST,
                    if (detail.isEmpty()) "请求参数异常，请重试或反馈" else "请求参数异常：$detail",
                    false,
                    requestId
                )
            }
        }
        404 -> LlmError(LlmError.Kind.BAD_REQUEST, "接口或模型不存在（404）", false, requestId)
        500, 503 -> LlmError(LlmError.Kind.SERVER, "HTTP $code：服务暂时不可用，请稍后重试", true, requestId)
        else -> LlmError(LlmError.Kind.UNKNOWN, "请求失败（$code）", false, requestId)
    }
}

class LlmException(val error: LlmError) : Exception(error.message)
