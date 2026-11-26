package com.dsha.sagittarius.agent.llm

import com.dsha.sagittarius.agent.engine.ChatMessage
import com.dsha.sagittarius.agent.engine.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmJsonTest {

    @Test
    fun `tool call message carries empty reasoning and content`() {
        val request = LlmRequest(
            model = "deepseek-flash",
            messages = listOf(
                ChatMessage(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(ToolCall("c1", "add_expense", "{}"))
                )
            ),
            tools = JsonArray(emptyList()),
            thinking = true,
            effort = "high",
            maxTokens = 4096
        )
        val body = Json.parseToJsonElement(
            LlmJson.requestBody(request, ProviderRegistry.get("deepseek")!!)
        ).jsonObject
        val message = body["messages"]!!.jsonArray.first().jsonObject
        assertEquals("", message["content"]!!.jsonPrimitive.content)
        assertEquals("", message["reasoning_content"]!!.jsonPrimitive.content)
    }
}
