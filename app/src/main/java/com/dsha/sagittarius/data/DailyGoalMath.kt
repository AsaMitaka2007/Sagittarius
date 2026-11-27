package com.dsha.sagittarius.data

import java.time.LocalDate

/** 当日目标的营养效果与文案。 */
object DailyGoalMath {
    val keys = listOf("water", "protein", "carbs", "fat", "fiber", "calories", "sodium")

    fun label(key: String, amount: Double): String = when (key) {
        "water" -> "水 +${fmt(amount)} g"
        "protein" -> "蛋白质 +${fmt(amount)} g"
        "carbs" -> "碳水 +${fmt(amount)} g"
        "fat" -> "脂肪 +${fmt(amount)} g"
        "fiber" -> "纤维 +${fmt(amount)} g"
        "calories" -> "热量 +${fmt(amount)} kcal"
        "sodium" -> "钠 +${fmt(amount)} mg"
        else -> "$key +${fmt(amount)}"
    }

    fun isCompleted(goal: DailyGoalEntity, today: LocalDate = LocalDate.now()): Boolean =
        goal.completedDate == today.toString()

    fun apply(
        base: NutritionValues,
        goals: List<DailyGoalEntity>,
        today: LocalDate = LocalDate.now()
    ): NutritionValues {
        var water = base.water
        var protein = base.protein
        var carbs = base.carbs
        var fat = base.fat
        var fiber = base.fiber
        var calories = base.calories
        var sodium = base.sodium
        goals.filter { isCompleted(it, today) }.forEach { goal ->
            when (goal.nutrientKey) {
                "water" -> water += goal.amount
                "protein" -> protein += goal.amount
                "carbs" -> carbs += goal.amount
                "fat" -> fat += goal.amount
                "fiber" -> fiber += goal.amount
                "calories" -> calories += goal.amount
                "sodium" -> sodium += goal.amount
            }
        }
        return base.copy(
            water = water,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            calories = calories,
            sodium = sodium
        )
    }

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
}
