package com.dsha.sagittarius.agent.debug

import android.util.Log
import com.dsha.sagittarius.BuildConfig

interface AgentDebugLogger {
    fun logRequest(url: String, json: String)
    fun logSseEvent(raw: String)
    fun logResponse(raw: String) = logSseEvent(raw)
    fun logTool(name: String, args: String, result: String, durationMs: Long)
    fun logHttpError(code: Int, requestId: String?, body: String) = Unit
    fun logError(t: Throwable, requestId: String? = null)
}

object NoOpAgentDebugLogger : AgentDebugLogger {
    override fun logRequest(url: String, json: String) = Unit
    override fun logSseEvent(raw: String) = Unit
    override fun logResponse(raw: String) = Unit
    override fun logTool(name: String, args: String, result: String, durationMs: Long) = Unit
    override fun logError(t: Throwable, requestId: String?) = Unit
}

object LogcatDebugLogger : AgentDebugLogger {
    private const val TAG = "Sagittarius"

    private fun enabled() = BuildConfig.DEBUG

    private fun truncate(value: String, max: Int = 8 * 1024): String =
        if (value.length <= max) value else value.take(max) + "…"

    private fun sanitize(value: String): String =
        value.replace(Regex("Bearer\\s+sk-[A-Za-z0-9_-]+"), "Bearer sk-***")

    override fun logRequest(url: String, json: String) {
        SagittariusLogger.add("REQUEST", "$url\n$json")
        if (enabled()) Log.d(TAG, "request ${sanitize(url)} ${sanitize(truncate(json))}")
    }

    override fun logSseEvent(raw: String) {
        SagittariusLogger.add("SSE", raw)
        if (enabled()) Log.d(TAG, "sse ${sanitize(truncate(raw, 1024))}")
    }

    override fun logResponse(raw: String) {
        SagittariusLogger.add("RESPONSE", raw)
        if (enabled()) Log.d(TAG, "response ${sanitize(truncate(raw, 8192))}")
    }

    override fun logTool(name: String, args: String, result: String, durationMs: Long) {
        SagittariusLogger.add("TOOL", "$name $args -> $result (${durationMs}ms)")
        if (enabled()) Log.d(TAG, "tool $name ${truncate(args, 512)} ${truncate(result, 512)} ${durationMs}ms")
    }

    override fun logHttpError(code: Int, requestId: String?, body: String) {
        SagittariusLogger.add("HTTP", "code=$code requestId=$requestId body=$body")
        if (enabled()) {
            Log.e(TAG, "http $code requestId=$requestId body=${sanitize(truncate(body, 2048))}")
        }
    }

    override fun logError(t: Throwable, requestId: String?) {
        SagittariusLogger.add("EXCEPTION", "requestId=$requestId\n" + t.stackTraceToString())
        if (enabled()) Log.e(TAG, "error requestId=$requestId", t)
    }
}
