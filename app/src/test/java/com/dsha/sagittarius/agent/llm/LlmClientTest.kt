package com.dsha.sagittarius.agent.llm

import com.dsha.sagittarius.agent.engine.ChatMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun parsesStandardMessageReasoningAndToolCalls() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"content":"好","reasoning_content":"想","tool_calls":[{"id":"c1","type":"function","function":{"name":"add_expense","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":2}}"""
            )
        )
        val client = OkHttpLlmClient(server.url("/").toString(), apiKeyProvider = { "sk-test" })
        val events = client.stream(
            LlmRequest("deepseek-flash", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()), false, "off", 4096)
        ).toList()
        assertTrue(events.any { it is LlmEvent.ReasoningDelta && it.text == "想" })
        assertTrue(events.any { it is LlmEvent.ContentDelta && it.text == "好" })
        assertTrue(events.any { it is LlmEvent.ToolCallDelta && it.name == "add_expense" })
        assertTrue(events.any { it is LlmEvent.Completed })
        val request = server.takeRequest()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertFalse(body["stream"].toString().toBoolean())
        assertTrue(body.containsKey("tools"))
    }

    @Test fun emptyContentFallsBackToReasoning() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"content":null,"reasoning_content":"推理答案"},"finish_reason":"stop"}]}"""
            )
        )
        val client = OkHttpLlmClient(server.url("/").toString(), apiKeyProvider = { "sk" })
        val events = client.stream(
            LlmRequest("deepseek-flash", listOf(ChatMessage("user", "你好")), JsonArray(emptyList()), false, "off", 4096)
        ).toList()
        assertTrue(events.any { it is LlmEvent.ContentDelta && it.text == "推理答案" })
    }

    @Test fun deepSeekEffortAndGlmForcedThinking() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}"""))
        val glm = ProviderRegistry.get("zhipu")!!
        val client = OkHttpLlmClient(server.url("/").toString(), apiKeyProvider = { "sk" }, provider = glm)
        client.stream(
            LlmRequest("glm-5.3-flash", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()), thinking = false, effort = "off", maxTokens = 4096)
        ).toList()
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("enabled", body["thinking"]!!.jsonObject["type"].toString().trim('"'))
        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test fun mapsUnauthorizedWithCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val client = OkHttpLlmClient(server.url("/").toString(), apiKeyProvider = { "bad" })
        val events = client.stream(
            LlmRequest("deepseek-flash", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()), false, "off", 4096)
        ).toList()
        val failure = events.filterIsInstance<LlmEvent.Failed>().first()
        assertEquals(LlmError.Kind.AUTH, failure.error.kind)
        assertTrue(failure.error.message.contains("401"))
    }

    @Test fun malformedJsonBecomesParseErrorNotSilence() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val client = OkHttpLlmClient(server.url("/").toString(), apiKeyProvider = { "sk" })
        val events = client.stream(
            LlmRequest("deepseek-flash", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()), false, "off", 4096)
        ).toList()
        val failure = events.filterIsInstance<LlmEvent.Failed>().first()
        assertEquals(LlmError.Kind.PARSE, failure.error.kind)
        assertTrue(failure.error.message.contains("JSON"))
    }
}
