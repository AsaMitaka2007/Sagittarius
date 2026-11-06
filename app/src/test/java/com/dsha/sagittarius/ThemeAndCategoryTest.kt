package com.dsha.sagittarius

import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAndCategoryTest {

    @Test
    fun `theme storage defaults to black and round trips`() {
        assertEquals(ThemeMode.BLACK, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.BLACK, ThemeMode.fromStorage("unknown"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.BLACK, ThemeMode.fromStorage("black"))
        assertEquals(ThemeMode.GLASS, ThemeMode.fromStorage("glass"))
    }

    @Test
    fun `preset categories have stable unique ids`() {
        val presets = CategoryCatalog.presetEntities
        assertEquals(11, presets.size)
        assertEquals(11, presets.map { it.id }.toSet().size)
        assertEquals(11, presets.map { it.systemKey }.toSet().size)
        assertEquals(listOf("餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他", "日用", "书籍", "游戏"), presets.map { it.name })
        assertTrue(presets.all { !it.isCustom && it.systemKey != null })
    }
}
