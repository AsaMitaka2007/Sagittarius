package com.dsha.sagittarius.agent.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SystemPromptProviderTest {
    @Test fun containsCurrentTimeAndMealGuidance() {
        val prompt = SystemPromptProvider(
            Clock.fixed(Instant.parse("2024-05-20T04:30:00Z"), ZoneOffset.UTC)
        ).build()
        assertTrue(prompt.contains("2024-05-20 04:30:00"))
        assertTrue(prompt.contains("早餐"))
        assertTrue(prompt.contains("餐次按时间推断"))
        assertTrue(prompt.contains("search_food_cn_local"))
        assertTrue(prompt.contains("toggle_network_search"))
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("list_categories"))
        assertTrue(prompt.contains("category_id"))
        assertTrue(prompt.contains("YYYY-MM-DD"))
    }
}
