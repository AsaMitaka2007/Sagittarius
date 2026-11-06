package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.debug.AgentDebugLogger
import com.dsha.sagittarius.agent.debug.NoOpAgentDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WebSearchTool(
    private val apiKeyProvider: suspend () -> String?,
    private val baseUrl: String = "https://api.bochaai.com/v1/web-search",
    private val logger: AgentDebugLogger = NoOpAgentDebugLogger
) : AgentTool {
    override val name = "web_search"
    override val description = "当本地中文营养库找不到时，使用博查进行全网网页搜索"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "要搜索的问题，例如某食物的每100g营养")
            })
        })
        put("required", buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("query"))
        })
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val query = args["query"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (query.isEmpty()) return@withContext unavailable()
        val key = apiKeyProvider()
        if (key.isNullOrBlank()) return@withContext unavailable()
        val requestJson = buildJsonObject {
            put("query", query)
            put("summary", true)
            put("count", 3)
        }.toString()
        logger.logRequest(baseUrl, requestJson)
        val raw = runCatching {
            client.newCall(
                Request.Builder()
                    .url(baseUrl)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute().use { response ->
                val body = response.body?.string().orEmpty()
                logger.logResponse(body)
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                body
            }
        }.getOrElse {
            logger.logError(it)
            return@withContext unavailable()
        }
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return@withContext unavailable()
        val data = root["data"] as? JsonObject
        val pages = (data?.get("webPages") as? JsonObject)?.get("value") as? JsonArray
            ?: (root["webPages"] as? JsonObject)?.get("value") as? JsonArray
            ?: JsonArray(emptyList())
        val results = pages.take(3).mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = (obj["name"] ?: obj["title"])?.jsonPrimitive?.content.orEmpty()
            val url = obj["url"]?.jsonPrimitive?.content.orEmpty()
            val summary = (obj["summary"] ?: obj["snippet"] ?: obj["content"])?.jsonPrimitive?.content.orEmpty()
            if (title.isBlank() && summary.isBlank()) null
            else WebResult(title, url, summary)
        }
        if (results.isEmpty()) return@withContext unavailable()
        ToolResult(
            ok = true,
            contentForModel = buildJsonObject {
                put("ok", true)
                put("source", "博查网页搜索")
                put("results", buildJsonArray {
                    results.forEach { result ->
                        add(buildJsonObject {
                            put("title", result.title)
                            put("url", result.url)
                            put("summary", result.summary)
                        })
                    }
                })
            }.toString(),
            display = "博查网页搜索：${results.first().title.take(40)}"
        )
    }

    private fun unavailable() = ToolResult(
        ok = false,
        contentForModel = buildJsonObject {
            put("ok", false)
            put("message", "【系统提示】网页搜索接口当前不可用。请直接使用内置知识估算，并告诉用户当前为离线回答。")
        }.toString()
    )

    private data class WebResult(val title: String, val url: String, val summary: String)
}
