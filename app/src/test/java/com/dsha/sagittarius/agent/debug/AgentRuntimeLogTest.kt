package com.dsha.sagittarius.agent.debug

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRuntimeLogTest {

    @After
    fun tearDown() {
        AgentRuntimeLog.clear()
    }

    @Test
    fun redactsApiKeys() {
        AgentRuntimeLog.info("key=sk-abcdefgh12345678 Authorization: Bearer abcdefgh12345678")
        val text = AgentRuntimeLog.format(AgentRuntimeLog.snapshot())
        assertTrue(text.contains("sk-***"))
        assertTrue(text.contains("Bearer ***"))
        assertFalse(text.contains("abcdefgh12345678"))
    }

    @Test
    fun keepsLastEntries() {
        repeat(10) { AgentRuntimeLog.info("line-$it") }
        val entries = AgentRuntimeLog.snapshot()
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.last().message.contains("line-9"))
    }
}
