package com.dsha.sagittarius.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyGoalMathTest {

    private val today = LocalDate.of(2026, 11, 27)
    private val base = NutritionValues(
        calories = 100.0, carbs = 10.0, fat = 5.0, protein = 8.0,
        fiber = 3.0, vitamin = 0.0, water = 200.0
    )

    @Test
    fun `completed water goal adds to water only`() {
        val goal = DailyGoalEntity(
            id = 1, title = "每日 1500ml 水", nutrientKey = "water",
            amount = 1500.0, completedDate = today.toString()
        )
        val result = DailyGoalMath.apply(base, listOf(goal), today)
        assertEquals(1700.0, result.water, 0.001)
        assertEquals(100.0, result.calories, 0.001)
        assertEquals(8.0, result.protein, 0.001)
    }

    @Test
    fun `yesterday's completed goal is reset today`() {
        val goal = DailyGoalEntity(
            id = 1, title = "蛋白质", nutrientKey = "protein",
            amount = 30.0, completedDate = today.minusDays(1).toString()
        )
        val result = DailyGoalMath.apply(base, listOf(goal), today)
        assertEquals(8.0, result.protein, 0.001)
    }

    @Test
    fun `multiple completed goals stack`() {
        val goals = listOf(
            DailyGoalEntity(title = "水", nutrientKey = "water", amount = 1000.0, completedDate = today.toString()),
            DailyGoalEntity(title = "水2", nutrientKey = "water", amount = 500.0, completedDate = today.toString()),
            DailyGoalEntity(title = "钠", nutrientKey = "sodium", amount = 300.0, completedDate = today.toString())
        )
        val result = DailyGoalMath.apply(base, goals, today)
        assertEquals(1700.0, result.water, 0.001)
        assertEquals(300.0, result.sodium, 0.001)
    }
}
