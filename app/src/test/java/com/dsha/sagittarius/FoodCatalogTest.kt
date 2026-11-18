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
}
