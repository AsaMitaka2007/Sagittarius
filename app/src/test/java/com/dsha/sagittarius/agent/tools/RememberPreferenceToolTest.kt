package com.dsha.sagittarius.agent.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberPreferenceToolTest {

    @Test fun savesNormalizedMemory() = runBlocking {
        var savedCategory = ""
        var savedContent = ""
        val tool = RememberPreferenceTool { category, content ->
            savedCategory = category
            savedContent = content
            true
        }
        val args = Json.parseToJsonElement(
            """{"category":"饮食习惯","content":"以后记录蛋白粉自动加 500ml 水"}"""
        ).jsonObject
        val result = tool.execute(args)
        assertTrue(result.ok)
        assertEquals("饮食习惯", savedCategory)
        assertEquals("以后记录蛋白粉自动加 500ml 水", savedContent)
    }

    @Test fun unknownCategoryFallsBackToOther() = runBlocking {
        var savedCategory = ""
        val tool = RememberPreferenceTool { category, _ ->
            savedCategory = category
            true
        }
        val args = Json.parseToJsonElement(
            """{"category":"随便","content":"少放盐"}"""
        ).jsonObject
        assertTrue(tool.execute(args).ok)
        assertEquals("其他", savedCategory)
    }

    @Test fun blankContentFails() = runBlocking {
        val tool = RememberPreferenceTool { _, _ -> true }
        val args = Json.parseToJsonElement("""{"category":"其他","content":"  "}""").jsonObject
        assertFalse(tool.execute(args).ok)
    }
}
