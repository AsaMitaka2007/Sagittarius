package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.SettingsPort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAppSettingsToolTest {
    private class FakeSettings : SettingsPort {
        var theme = ""
        var budget = 0L
        var goal = ""
        override suspend fun setTheme(value: String): String { theme = value; return value }
        override suspend fun setDailyBudget(amountCents: Long): Boolean { budget = amountCents; return true }
        override suspend fun setNutritionGoal(value: String): String { goal = value; return "营养目标已更新" }
    }

    @Test fun updatesThemeBudgetAndGoal() = kotlinx.coroutines.runBlocking {
        val fake = FakeSettings()
        val tool = UpdateAppSettingsTool(fake)
        assertTrue(tool.execute(Json.parseToJsonElement("""{"setting_key":"theme","value":"dark"}""").jsonObject).ok)
        assertEquals("dark", fake.theme)
        assertTrue(tool.execute(Json.parseToJsonElement("""{"setting_key":"daily_budget","value":"200"}""").jsonObject).ok)
        assertEquals(20000L, fake.budget)
        assertTrue(tool.execute(Json.parseToJsonElement("""{"setting_key":"nutrition_goal","value":"150g"}""").jsonObject).ok)
        assertEquals("150g", fake.goal)
    }
}
