package com.dsha.sagittarius.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test fun parsesSplitChunksAndCrLf() {
        val parser = SseParser()
        assertEquals(emptyList<String>(), parser.feed("data: {\"a\":"))
        assertEquals(listOf("{\"a\":1}"), parser.feed("1}\r\n"))
    }

    @Test fun ignoresCommentsAndBlankLines() {
        val parser = SseParser()
        assertEquals(emptyList<String>(), parser.feed(": ping\n\n"))
    }

    @Test fun handlesDoneAndFinish() {
        val parser = SseParser()
        assertEquals(listOf("[DONE]"), parser.feed("data: [DONE]\n"))
        assertEquals(emptyList<String>(), parser.feed("data: {\"x\":2}"))
        assertEquals(listOf("{\"x\":2}"), parser.finish())
    }
}
