package com.dsha.sagittarius.agent.engine

import com.dsha.sagittarius.agent.llm.LlmClient
import com.dsha.sagittarius.agent.llm.LlmEvent
import com.dsha.sagittarius.agent.llm.LlmRequest
import com.dsha.sagittarius.agent.llm.ModelInfo
import com.dsha.sagittarius.agent.tools.AgentTool
import com.dsha.sagittarius.agent.tools.ToolRegistry
import com.dsha.sagittarius.agent.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AgentEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC)

    private class FakeTool(override val name: String = "add_expense") : AgentTool {
        override val description = "fake"
        override val parametersSchema = JsonObject(emptyMap())
        var calls = 0
        override suspend fun execute(args: JsonObject): ToolResult {
            calls++
            return ToolResult(true, """{"ok":true}""", "ok")
        }
    }

    private class FakeClient(private val responses: List<List<LlmEvent>>) : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
            requests += request
            responses.getOrElse(requests.size - 1) { responses.last() }.forEach { emit(it) }
        }
        override suspend fun fetchModels() = listOf(ModelInfo("deepseek-flash", "Flash"))
    }

    @Test fun textOnlyFinishes() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(listOf(listOf(LlmEvent.ContentDelta("你好"), LlmEvent.Completed("stop"))))
        val engine = AgentEngine(client, ToolRegistry(), SystemPromptProvider(clock))
        val events = engine.send("hi", "deepseek-flash", "off").toList()
        assertTrue(events.any { it is AgentEvent.TextDelta })
        assertTrue(events.any { it is AgentEvent.Finished })
        assertEquals(1, client.requests.size)
    }

    @Test fun toolLoopPassesReasoningAndExecutesOnceForDuplicate() = kotlinx.coroutines.runBlocking {
        val tool = FakeTool()
        val registry = ToolRegistry().apply { register(tool) }
        val client = FakeClient(listOf(
            listOf(
                LlmEvent.ReasoningDelta("think"),
                LlmEvent.ToolCallDelta(0, "call1", "add_expense", """{"amount":1}"""),
                LlmEvent.ToolCallDelta(1, "call2", "add_expense", """{"amount":1}"""),
                LlmEvent.Completed("tool_calls")
            ),
            listOf(LlmEvent.ContentDelta("完成"), LlmEvent.Completed("stop"))
        ))
        val engine = AgentEngine(client, registry, SystemPromptProvider(clock))
        val events = engine.send("记一笔", "deepseek-flash", "high").toList()
        assertEquals(1, tool.calls)
        assertTrue(events.any { it is AgentEvent.ToolFinished })
        assertTrue(events.any { it is AgentEvent.Finished })
        val second = client.requests[1]
        val assistant = second.messages.first { it.role == "assistant" }
        assertEquals("think", assistant.reasoningContent)
    }

    @Test fun stopsAfterFiveToolRounds() = kotlinx.coroutines.runBlocking {
        val tool = FakeTool()
        val registry = ToolRegistry().apply { register(tool) }
        val response = listOf(
            LlmEvent.ToolCallDelta(0, "call", "add_expense", "{}"),
            LlmEvent.Completed("tool_calls")
        )
        val client = FakeClient(List(6) { response })
        val engine = AgentEngine(client, registry, SystemPromptProvider(clock))
        val events = engine.send("loop", "deepseek-flash", "off").toList()
        assertTrue(events.any { it is AgentEvent.Failed })
        assertEquals(5, tool.calls)
    }

    @Test fun emptyResponseDoesNotPoisonHistory() = kotlinx.coroutines.runBlocking {
        val client = FakeClient(listOf(
            listOf(LlmEvent.Completed("stop")),
            listOf(LlmEvent.ContentDelta("ok"), LlmEvent.Completed("stop"))
        ))
        val engine = AgentEngine(client, ToolRegistry(), SystemPromptProvider(clock))
        val first = engine.send("hi", "deepseek-flash", "off").toList()
        assertTrue(first.any { it is AgentEvent.Failed })
        val second = engine.send("again", "deepseek-flash", "off").toList()
        assertTrue(second.any { it is AgentEvent.TextDelta })
        val secondRequest = client.requests[1]
        assertTrue(secondRequest.messages.none {
            it.role == "assistant" && it.content.isNullOrBlank() && it.toolCalls.isEmpty()
        })
    }
}
