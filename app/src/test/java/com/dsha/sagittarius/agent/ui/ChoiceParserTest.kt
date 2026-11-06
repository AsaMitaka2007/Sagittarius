package com.dsha.sagittarius.agent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoiceParserTest {
    @Test fun parsesJsonChoicesAndRemovesBlock() {
        val result = ChoiceParser.parse(
            """要放在哪里？[CHOICES]{"options":[{"label":"其他","value":"用其他分类"},{"label":"新建存款","value":"新建存款分类"}]}[/CHOICES]"""
        )
        assertEquals("要放在哪里？", result.text)
        assertEquals(2, result.choices.size)
        assertEquals("其他", result.choices[0].label)
        assertEquals("新建存款分类", result.choices[1].value)
    }

    @Test fun parsesLineFallback() {
        val result = ChoiceParser.parse("请选择[CHOICES]\n其他\n新建存款|创建存款分类\n[/CHOICES]")
        assertEquals("请选择", result.text)
        assertEquals(2, result.choices.size)
        assertEquals("创建存款分类", result.choices[1].value)
    }

    @Test fun hidesIncompleteBlockWhileStreaming() {
        val result = ChoiceParser.parse("稍等[CHOICES]{\"options\":[{\"label\":\"其他\"")
        assertEquals("稍等", result.text)
        assertTrue(result.choices.isEmpty())
    }
}
