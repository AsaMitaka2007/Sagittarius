package com.dsha.sagittarius.agent.tools

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    private class T(override val name: String) : AgentTool {
        override val description = "test"
        override val parametersSchema = JsonObject(emptyMap())
        override suspend fun execute(args: JsonObject) = ToolResult(true, "{}")
    }

    @Test fun duplicateFailsFast() {
        val registry = ToolRegistry()
        registry.register(T("a"))
        try {
            registry.register(T("a"))
            error("expected failure")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun schemasAndLookup() {
        val registry = ToolRegistry().apply { register(T("a")) }
        assertNotNull(registry.get("a"))
        assertNull(registry.get("b"))
        assertTrue(registry.schemas().size == 1)
    }
}
