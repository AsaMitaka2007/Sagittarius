package com.dsha.sagittarius.agent.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebSearchToolTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun returnsTopThreeAndGracefulFailure() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"webPages":{"value":[{"name":"螺蛳粉营养","url":"https://a","summary":"每100g约350kcal"},{"name":"b","url":"https://b","summary":"b"},{"name":"c","url":"https://c","summary":"c"},{"name":"d","url":"https://d","summary":"d"}]}}}"""
            )
        )
        val tool = WebSearchTool({ "bocha-key" }, server.url("/").toString())
        val result = tool.execute(Json.parseToJsonElement("""{"query":"螺蛳粉 热量"}""").jsonObject)
        assertTrue(result.ok)
        assertTrue(result.contentForModel.contains("螺蛳粉营养"))
        assertTrue(!result.contentForModel.contains("\"d\""))
        server.enqueue(MockResponse().setResponseCode(500))
        val failed = tool.execute(Json.parseToJsonElement("""{"query":"x"}""").jsonObject)
        assertFalse(failed.ok)
        assertTrue(failed.contentForModel.contains("网页搜索接口当前不可用"))
    }

    @Test fun missingKeyIsGraceful() = runBlocking {
        val tool = WebSearchTool({ null }, server.url("/").toString())
        val result = tool.execute(Json.parseToJsonElement("""{"query":"x"}""").jsonObject)
        assertFalse(result.ok)
        assertTrue(result.contentForModel.contains("内置知识估算"))
    }
}
