package com.dsha.sagittarius.data

object FoodCatalog {

    private fun food(
        order: Int,
        name: String,
        icon: String,
        unit: String = "g",
        weightPerUnit: Double = 1.0,
        kcal: Double,
        carbs: Double,
        fat: Double,
        protein: Double,
        fiber: Double,
        vitamin: Double,
        water: Double
    ) = FoodEntity(
        name = name,
        icon = icon,
        // 预置食物基础单位永远是 1g=1g；自定义换算放入 food_units。
        unit = "g",
        weightPerUnit = 1.0,
        caloriesPer100g = kcal,
        carbsPer100g = carbs,
        fatPer100g = fat,
        proteinPer100g = protein,
        fiberPer100g = fiber,
        vitaminPer100g = vitamin,
        waterPer100g = water,
        category = when (name) {
            "苹果", "香蕉", "西兰花", "胡萝卜", "番茄", "黄瓜", "菠菜",
            "西瓜", "橙子", "葡萄", "草莓", "红薯", "紫薯", "土豆", "玉米", "海带" -> "FRUIT_VEG"
            "鸡胸肉", "鸡蛋", "牛奶", "酸奶", "牛肉", "猪肉", "三文鱼",
            "虾仁", "鸡腿", "豆腐", "豆腐皮", "豆浆" -> "MEAT_EGG"
            "坚果", "花生酱", "咖啡", "全麦面包" -> "SNACK"
            else -> "STAPLE"
        },
        sortOrder = order
    )

    val presets: List<FoodEntity> = listOf(
        food(0, "米饭", "🍚", kcal = 116.0, carbs = 25.9, fat = 0.3, protein = 2.6, fiber = 0.3, vitamin = 0.0, water = 70.9),
        food(1, "鸡胸肉", "🍗", kcal = 133.0, carbs = 0.0, fat = 5.0, protein = 24.6, fiber = 0.0, vitamin = 0.3, water = 69.0),
        food(2, "苹果", "🍎", kcal = 53.0, carbs = 13.7, fat = 0.2, protein = 0.4, fiber = 1.7, vitamin = 4.6, water = 85.0),
        food(3, "牛奶", "🥛", "份", 250.0, 54.0, 5.0, 3.2, 3.0, 0.0, 1.0, 89.8),
        food(4, "鸡蛋", "🥚", "个", 50.0, 144.0, 1.3, 8.8, 13.3, 0.0, 0.5, 74.0),
        food(5, "香蕉", "🍌", kcal = 93.0, carbs = 22.0, fat = 0.2, protein = 1.4, fiber = 1.2, vitamin = 8.7, water = 75.0),
        food(6, "燕麦", "🥣", kcal = 377.0, carbs = 66.2, fat = 6.7, protein = 15.0, fiber = 5.3, vitamin = 0.0, water = 8.6),
        food(7, "全麦面包", "🍞", "片", 35.0, 246.0, 41.0, 4.2, 12.0, 6.0, 0.0, 38.0),
        food(8, "红薯", "🍠", kcal = 86.0, carbs = 20.1, fat = 0.1, protein = 1.6, fiber = 3.0, vitamin = 2.4, water = 77.0),
        food(9, "西兰花", "🥦", kcal = 34.0, carbs = 6.6, fat = 0.4, protein = 2.8, fiber = 2.6, vitamin = 89.2, water = 89.0),
        food(10, "胡萝卜", "🥕", kcal = 41.0, carbs = 9.6, fat = 0.2, protein = 0.9, fiber = 2.8, vitamin = 5.9, water = 88.0),
        food(11, "番茄", "🍅", kcal = 18.0, carbs = 3.9, fat = 0.2, protein = 0.9, fiber = 1.2, vitamin = 13.7, water = 94.5),
        food(12, "黄瓜", "🥒", kcal = 16.0, carbs = 3.6, fat = 0.1, protein = 0.7, fiber = 0.5, vitamin = 2.8, water = 95.2),
        food(13, "菠菜", "🥬", kcal = 23.0, carbs = 3.6, fat = 0.4, protein = 2.9, fiber = 2.2, vitamin = 28.1, water = 91.4),
        food(14, "豆腐", "🧈", kcal = 84.0, carbs = 3.0, fat = 5.0, protein = 8.0, fiber = 0.4, vitamin = 0.0, water = 82.0),
        food(15, "牛肉", "🥩", kcal = 250.0, carbs = 0.0, fat = 15.0, protein = 26.0, fiber = 0.0, vitamin = 0.0, water = 58.0),
        food(16, "猪肉", "🥓", kcal = 242.0, carbs = 0.0, fat = 14.0, protein = 27.0, fiber = 0.0, vitamin = 0.0, water = 58.0),
        food(17, "三文鱼", "🐟", kcal = 208.0, carbs = 0.0, fat = 13.0, protein = 20.0, fiber = 0.0, vitamin = 1.0, water = 64.0),
        food(18, "虾仁", "🦐", kcal = 99.0, carbs = 0.2, fat = 0.3, protein = 24.0, fiber = 0.0, vitamin = 0.0, water = 75.0),
        food(19, "酸奶", "🥛", "杯", 200.0, 72.0, 9.3, 2.7, 2.5, 0.0, 1.0, 85.0),
        food(20, "坚果", "🥜", kcal = 607.0, carbs = 21.0, fat = 54.0, protein = 20.0, fiber = 7.0, vitamin = 0.5, water = 4.0),
        food(21, "花生酱", "🥜", kcal = 588.0, carbs = 20.0, fat = 50.0, protein = 25.0, fiber = 6.0, vitamin = 0.0, water = 1.0),
        food(22, "面条", "🍜", kcal = 138.0, carbs = 28.0, fat = 0.5, protein = 4.5, fiber = 1.0, vitamin = 0.0, water = 72.0),
        food(23, "饺子", "🥟", "个", 20.0, 220.0, 26.0, 8.0, 8.0, 1.0, 0.0, 55.0),
        food(24, "土豆", "🥔", kcal = 77.0, carbs = 17.0, fat = 0.1, protein = 2.0, fiber = 2.2, vitamin = 1.0, water = 79.0),
        food(25, "玉米", "🌽", kcal = 112.0, carbs = 22.8, fat = 1.2, protein = 4.0, fiber = 2.9, vitamin = 1.0, water = 72.0),
        food(26, "西瓜", "🍉", kcal = 30.0, carbs = 7.6, fat = 0.2, protein = 0.6, fiber = 0.4, vitamin = 8.1, water = 91.4),
        food(27, "橙子", "🍊", kcal = 47.0, carbs = 11.8, fat = 0.1, protein = 0.9, fiber = 2.4, vitamin = 33.0, water = 87.0),
        food(28, "葡萄", "🍇", kcal = 69.0, carbs = 18.1, fat = 0.2, protein = 0.7, fiber = 0.9, vitamin = 3.2, water = 80.0),
        food(29, "草莓", "🍓", kcal = 32.0, carbs = 7.7, fat = 0.3, protein = 0.7, fiber = 2.0, vitamin = 47.0, water = 91.0),
        food(30, "咖啡", "☕", "杯", 240.0, 2.0, 0.0, 0.0, 0.1, 0.0, 0.0, 99.0),
        food(31, "豆浆", "🥛", "杯", 250.0, 31.0, 1.2, 1.6, 3.0, 0.0, 0.0, 93.0),
        food(32, "紫薯", "🍠", kcal = 82.0, carbs = 18.0, fat = 0.1, protein = 1.6, fiber = 3.0, vitamin = 2.0, water = 77.0),
        food(33, "鸡腿", "🍗", kcal = 181.0, carbs = 0.0, fat = 13.0, protein = 16.0, fiber = 0.0, vitamin = 0.2, water = 65.0),
        food(34, "豆腐皮", "🧈", kcal = 410.0, carbs = 18.0, fat = 23.0, protein = 44.0, fiber = 1.0, vitamin = 0.0, water = 8.0),
        food(35, "海带", "🌿", kcal = 13.0, carbs = 2.1, fat = 0.1, protein = 1.2, fiber = 0.5, vitamin = 0.0, water = 94.4)
    )

    val defaultGoal = NutritionGoalEntity()
}
