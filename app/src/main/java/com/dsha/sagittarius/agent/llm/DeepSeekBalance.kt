package com.dsha.sagittarius.agent.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** DeepSeek 官方余额查询，失败返回 null，不记录 Key。 */
object DeepSeekBalance {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(baseUrl: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/user/balance")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = LlmJson.json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val infos = root["balance_infos"] as? JsonArray ?: return@use null
                val info = infos.mapNotNull { it as? JsonObject }.firstOrNull {
                    it["currency"]?.jsonPrimitive?.content == "CNY"
                } ?: infos.firstOrNull() as? JsonObject ?: return@use null
                val value = info["total_balance"]?.jsonPrimitive?.content
                    ?: info["balance"]?.jsonPrimitive?.content
                    ?: return@use null
                val amount = value.toDoubleOrNull() ?: return@use null
                "¥%.2f".format(amount)
            }
        }.getOrNull()
    }
}
