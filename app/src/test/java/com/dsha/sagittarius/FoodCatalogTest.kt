package com.dsha.sagittarius

import com.dsha.sagittarius.data.FoodCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCatalogTest {

    @Test
    fun presetsHaveImagesAndNoEmoji() {
        assertEquals(36, FoodCatalog.presets.size)
        FoodCatalog.presets.forEachIndexed { index, food ->
            assertEquals(index, food.sortOrder)
            assertEquals("", food.icon)
            assertTrue(food.imageUri!!.startsWith("asset:food_images/food_"))
        }
    }

    @Test
    fun presetNamesAndOrdersAreUnique() {
        assertEquals(36, FoodCatalog.presets.map { it.name }.distinct().size)
        assertEquals(36, FoodCatalog.presets.map { it.sortOrder }.distinct().size)
    }

    @Test
    fun extrasCoverSupplementAndCombo() {
        assertEquals(2, FoodCatalog.extras.size)
        val powder = FoodCatalog.extras.first { it.category == "SUPPLEMENT" }
        assertEquals("酵母蛋白粉（75%）", powder.name)
        assertEquals(75.0, powder.proteinPer100g, 0.001)
        assertEquals(277.0, powder.caloriesPer100g, 0.001)
        assertEquals("杯", powder.unit)
        assertEquals(30.0, powder.weightPerUnit, 0.001)
        val rice = FoodCatalog.extras.first { it.category == "COMBO" }
        assertEquals("粤卤记猪脚饭", rice.name)
        assertEquals(550.0, rice.weightPerUnit, 0.001)
        assertEquals(162.0, rice.caloriesPer100g, 0.001)
    }
}
