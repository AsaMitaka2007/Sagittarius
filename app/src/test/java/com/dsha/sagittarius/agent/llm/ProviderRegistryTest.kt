package com.dsha.sagittarius.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test fun fourProvidersHaveExpectedCapabilities() {
        assertEquals(4, ProviderRegistry.all.size)
        val deepseek = ProviderRegistry.get("deepseek")!!
        assertEquals(ThinkingStyle.DEEPSEEK_EFFORT, deepseek.thinkingStyle)
        assertTrue(deepseek.canDisableThinking)
        assertTrue(deepseek.echoReasoningContent)

        val zhipu = ProviderRegistry.get("zhipu")!!
        assertEquals("glm-5.3-flash", zhipu.defaultModel)
        assertEquals(ThinkingStyle.TYPE_ONLY, zhipu.thinkingStyle)
        assertFalse(zhipu.canDisableThinking)

        val silicon = ProviderRegistry.get("siliconflow")!!
        assertEquals(ThinkingStyle.NONE, silicon.thinkingStyle)
        assertTrue(silicon.supportsTools)
        assertEquals("Qwen/Qwen3-8B", silicon.defaultModel)

        assertTrue(ProviderRegistry.get("openrouter")!!.fallbackModels.isNotEmpty())
    }
}
