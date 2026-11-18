package com.dsha.sagittarius.data

import java.util.Locale

/**
 * 预置食物库。
 *
 * 基础营养值沿用原有数据；钠与维生素细分值优先取自 `assets/cn_food.json`
 * 中的对应条目，取不到的条目在行尾注明估算来源。
 */
object FoodCatalog {

    private fun food(
        order: Int,
        name: String,
        unit: String = "g",
        weightPerUnit: Double = 1.0,
        kcal: Double,
        carbs: Double,
        fat: Double,
        protein: Double,
        fiber: Double,
        vitamin: Double,
        water: Double,
        sodium: Double,
        vitA: Double,
        vitB1: Double,
        vitB2: Double,
        vitB3: Double,
        vitC: Double,
        vitE: Double
    ) = FoodEntity(
        name = name,
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
        sodiumPer100g = sodium,
        vitaminAPer100g = vitA,
        vitaminB1Per100g = vitB1,
        vitaminB2Per100g = vitB2,
        vitaminB3Per100g = vitB3,
        vitaminCPer100g = vitC,
        vitaminEPer100g = vitE,
        category = when (name) {
            "苹果", "香蕉", "西兰花", "胡萝卜", "番茄", "黄瓜", "菠菜",
            "西瓜", "橙子", "葡萄", "草莓", "红薯", "紫薯", "土豆", "玉米", "海带" -> "FRUIT_VEG"
            "鸡胸肉", "鸡蛋", "牛奶", "酸奶", "牛肉", "猪肉", "三文鱼",
            "虾仁", "鸡腿", "豆腐", "豆腐皮", "豆浆" -> "MEAT_EGG"
            "坚果", "花生酱", "咖啡", "全麦面包" -> "SNACK"
            else -> "STAPLE"
        },
        imageUri = String.format(Locale.US, "asset:food_images/food_%02d.jpg", order),
        sortOrder = order
    )

    val presets: List<FoodEntity> = listOf(
        food(0, "米饭", kcal = 116.0, carbs = 25.9, fat = 0.3, protein = 2.6, fiber = 0.3, vitamin = 0.0, water = 70.9, sodium = 3.3, vitA = 0.0, vitB1 = 0.0, vitB2 = 0.03, vitB3 = 2.0, vitC = 0.0, vitE = 0.0),
        food(1, "鸡胸肉", kcal = 133.0, carbs = 0.0, fat = 5.0, protein = 24.6, fiber = 0.0, vitamin = 0.3, water = 69.0, sodium = 34.4, vitA = 16.0, vitB1 = 0.07, vitB2 = 0.13, vitB3 = 10.8, vitC = 0.0, vitE = 0.22),
        food(2, "苹果", kcal = 53.0, carbs = 13.7, fat = 0.2, protein = 0.4, fiber = 1.7, vitamin = 4.6, water = 85.0, sodium = 1.6, vitA = 3.0, vitB1 = 0.06, vitB2 = 0.02, vitB3 = 0.2, vitC = 4.0, vitE = 2.12),
        food(3, "牛奶", "份", 250.0, 54.0, 5.0, 3.2, 3.0, 0.0, 1.0, 89.8, sodium = 37.2, vitA = 24.0, vitB1 = 0.03, vitB2 = 0.14, vitB3 = 0.1, vitC = 1.0, vitE = 0.21),
        food(4, "鸡蛋", "个", 50.0, 144.0, 1.3, 8.8, 13.3, 0.0, 0.5, 74.0, sodium = 94.7, vitA = 310.0, vitB1 = 0.09, vitB2 = 0.31, vitB3 = 0.2, vitC = 0.0, vitE = 1.23),
        food(5, "香蕉", kcal = 93.0, carbs = 22.0, fat = 0.2, protein = 1.4, fiber = 1.2, vitamin = 8.7, water = 75.0, sodium = 0.8, vitA = 10.0, vitB1 = 0.02, vitB2 = 0.04, vitB3 = 0.7, vitC = 8.0, vitE = 0.24),
        food(6, "燕麦", kcal = 377.0, carbs = 66.2, fat = 6.7, protein = 15.0, fiber = 5.3, vitamin = 0.0, water = 8.6, sodium = 3.7, vitA = 0.0, vitB1 = 0.30, vitB2 = 0.13, vitB3 = 1.2, vitC = 0.0, vitE = 3.07),
        food(7, "全麦面包", "片", 35.0, 246.0, 41.0, 4.2, 12.0, 6.0, 0.0, 38.0, sodium = 457.0, vitA = 0.0, vitB1 = 0.03, vitB2 = 0.01, vitB3 = 0.0, vitC = 0.0, vitE = 0.88),
        food(8, "红薯", kcal = 86.0, carbs = 20.1, fat = 0.1, protein = 1.6, fiber = 3.0, vitamin = 2.4, water = 77.0, sodium = 28.5, vitA = 125.0, vitB1 = 0.04, vitB2 = 0.04, vitB3 = 0.6, vitC = 26.0, vitE = 0.28),
        food(9, "西兰花", kcal = 34.0, carbs = 6.6, fat = 0.4, protein = 2.8, fiber = 2.6, vitamin = 89.2, water = 89.0, sodium = 18.8, vitA = 1202.0, vitB1 = 0.09, vitB2 = 0.13, vitB3 = 0.9, vitC = 51.0, vitE = 0.91),
        food(10, "胡萝卜", kcal = 41.0, carbs = 9.6, fat = 0.2, protein = 0.9, fiber = 2.8, vitamin = 5.9, water = 88.0, sodium = 71.4, vitA = 688.0, vitB1 = 0.04, vitB2 = 0.03, vitB3 = 0.6, vitC = 13.0, vitE = 0.41),
        food(11, "番茄", kcal = 18.0, carbs = 3.9, fat = 0.2, protein = 0.9, fiber = 1.2, vitamin = 13.7, water = 94.5, sodium = 5.0, vitA = 92.0, vitB1 = 0.03, vitB2 = 0.03, vitB3 = 0.6, vitC = 19.0, vitE = 0.57),
        food(12, "黄瓜", kcal = 16.0, carbs = 3.6, fat = 0.1, protein = 0.7, fiber = 0.5, vitamin = 2.8, water = 95.2, sodium = 4.9, vitA = 15.0, vitB1 = 0.02, vitB2 = 0.03, vitB3 = 0.2, vitC = 9.0, vitE = 0.46),
        food(13, "菠菜", kcal = 23.0, carbs = 3.6, fat = 0.4, protein = 2.9, fiber = 2.2, vitamin = 28.1, water = 91.4, sodium = 85.2, vitA = 487.0, vitB1 = 0.04, vitB2 = 0.11, vitB3 = 0.6, vitC = 32.0, vitE = 1.74),
        food(14, "豆腐", kcal = 84.0, carbs = 3.0, fat = 5.0, protein = 8.0, fiber = 0.4, vitamin = 0.0, water = 82.0, sodium = 7.2, vitA = 0.0, vitB1 = 0.04, vitB2 = 0.03, vitB3 = 0.2, vitC = 0.0, vitE = 2.17),
        food(15, "牛肉", kcal = 250.0, carbs = 0.0, fat = 15.0, protein = 26.0, fiber = 0.0, vitamin = 0.0, water = 58.0, sodium = 57.4, vitA = 9.0, vitB1 = 0.03, vitB2 = 0.11, vitB3 = 7.4, vitC = 0.0, vitE = 0.22),
        food(16, "猪肉", kcal = 242.0, carbs = 0.0, fat = 14.0, protein = 27.0, fiber = 0.0, vitamin = 0.0, water = 58.0, sodium = 59.4, vitA = 0.0, vitB1 = 0.22, vitB2 = 0.16, vitB3 = 3.5, vitC = 0.0, vitE = 0.49),
        food(17, "三文鱼", kcal = 208.0, carbs = 0.0, fat = 13.0, protein = 20.0, fiber = 0.0, vitamin = 1.0, water = 64.0, sodium = 68.6, vitA = 12.0, vitB1 = 0.02, vitB2 = 0.07, vitB3 = 5.9, vitC = 0.0, vitE = 0.87),
        food(18, "虾仁", kcal = 99.0, carbs = 0.2, fat = 0.3, protein = 24.0, fiber = 0.0, vitamin = 0.0, water = 75.0, sodium = 133.6, vitA = 87.0, vitB1 = 0.02, vitB2 = 0.11, vitB3 = 0.9, vitC = 0.0, vitE = 3.92),
        food(19, "酸奶", "杯", 200.0, 72.0, 9.3, 2.7, 2.5, 0.0, 1.0, 85.0, sodium = 39.8, vitA = 26.0, vitB1 = 0.03, vitB2 = 0.15, vitB3 = 0.2, vitC = 1.0, vitE = 0.12),
        food(20, "坚果", kcal = 607.0, carbs = 21.0, fat = 54.0, protein = 20.0, fiber = 7.0, vitamin = 0.5, water = 4.0, sodium = 6.4, vitA = 5.0, vitB1 = 0.15, vitB2 = 0.14, vitB3 = 0.9, vitC = 1.0, vitE = 43.21),
        food(21, "花生酱", kcal = 588.0, carbs = 20.0, fat = 50.0, protein = 25.0, fiber = 6.0, vitamin = 0.0, water = 1.0, sodium = 2340.0, vitA = 0.0, vitB1 = 0.01, vitB2 = 0.15, vitB3 = 2.0, vitC = 0.0, vitE = 2.09),
        food(22, "面条", kcal = 138.0, carbs = 28.0, fat = 0.5, protein = 4.5, fiber = 1.0, vitamin = 0.0, water = 72.0, sodium = 3.4, vitA = 0.0, vitB1 = 0.35, vitB2 = 0.10, vitB3 = 3.1, vitC = 0.0, vitE = 0.47),
        // 饺子在营养库中没有对应条目，取常见猪肉白菜馅的估算值。
        food(23, "饺子", "个", 20.0, 220.0, 26.0, 8.0, 8.0, 1.0, 0.0, 55.0, sodium = 500.0, vitA = 5.0, vitB1 = 0.12, vitB2 = 0.08, vitB3 = 1.2, vitC = 0.0, vitE = 0.40),
        food(24, "土豆", kcal = 77.0, carbs = 17.0, fat = 0.1, protein = 2.0, fiber = 2.2, vitamin = 1.0, water = 79.0, sodium = 2.7, vitA = 5.0, vitB1 = 0.08, vitB2 = 0.04, vitB3 = 1.1, vitC = 27.0, vitE = 0.34),
        food(25, "玉米", kcal = 112.0, carbs = 22.8, fat = 1.2, protein = 4.0, fiber = 2.9, vitamin = 1.0, water = 72.0, sodium = 1.1, vitA = 0.0, vitB1 = 0.16, vitB2 = 0.11, vitB3 = 1.8, vitC = 0.0, vitE = 0.46),
        food(26, "西瓜", kcal = 30.0, carbs = 7.6, fat = 0.2, protein = 0.6, fiber = 0.4, vitamin = 8.1, water = 91.4, sodium = 3.2, vitA = 75.0, vitB1 = 0.02, vitB2 = 0.03, vitB3 = 0.2, vitC = 6.0, vitE = 0.10),
        food(27, "橙子", kcal = 47.0, carbs = 11.8, fat = 0.1, protein = 0.9, fiber = 2.4, vitamin = 33.0, water = 87.0, sodium = 1.2, vitA = 27.0, vitB1 = 0.05, vitB2 = 0.04, vitB3 = 0.3, vitC = 33.0, vitE = 0.56),
        food(28, "葡萄", kcal = 69.0, carbs = 18.1, fat = 0.2, protein = 0.7, fiber = 0.9, vitamin = 3.2, water = 80.0, sodium = 1.3, vitA = 8.0, vitB1 = 0.04, vitB2 = 0.02, vitB3 = 0.2, vitC = 25.0, vitE = 0.70),
        food(29, "草莓", kcal = 32.0, carbs = 7.7, fat = 0.3, protein = 0.7, fiber = 2.0, vitamin = 47.0, water = 91.0, sodium = 4.2, vitA = 5.0, vitB1 = 0.02, vitB2 = 0.03, vitB3 = 0.3, vitC = 47.0, vitE = 0.71),
        // 咖啡按黑咖啡估算，营养库中无对应条目。
        food(30, "咖啡", "杯", 240.0, 2.0, 0.0, 0.0, 0.1, 0.0, 0.0, 99.0, sodium = 2.0, vitA = 0.0, vitB1 = 0.0, vitB2 = 0.0, vitB3 = 0.0, vitC = 0.0, vitE = 0.0),
        food(31, "豆浆", "杯", 250.0, 31.0, 1.2, 1.6, 3.0, 0.0, 0.0, 93.0, sodium = 3.0, vitA = 15.0, vitB1 = 0.02, vitB2 = 0.02, vitB3 = 0.1, vitC = 0.0, vitE = 0.80),
        food(32, "紫薯", kcal = 82.0, carbs = 18.0, fat = 0.1, protein = 1.6, fiber = 3.0, vitamin = 2.0, water = 77.0, sodium = 58.2, vitA = 37.0, vitB1 = 0.07, vitB2 = 0.04, vitB3 = 0.6, vitC = 24.0, vitE = 0.43),
        food(33, "鸡腿", kcal = 181.0, carbs = 0.0, fat = 13.0, protein = 16.0, fiber = 0.0, vitamin = 0.2, water = 65.0, sodium = 64.4, vitA = 44.0, vitB1 = 0.02, vitB2 = 0.14, vitB3 = 6.0, vitC = 0.0, vitE = 0.03),
        food(34, "豆腐皮", kcal = 410.0, carbs = 18.0, fat = 23.0, protein = 44.0, fiber = 1.0, vitamin = 0.0, water = 8.0, sodium = 9.4, vitA = 0.0, vitB1 = 0.31, vitB2 = 0.11, vitB3 = 1.5, vitC = 0.0, vitE = 20.63),
        food(35, "海带", kcal = 13.0, carbs = 2.1, fat = 0.1, protein = 1.2, fiber = 0.5, vitamin = 0.0, water = 94.4, sodium = 8.6, vitA = 0.0, vitB1 = 0.02, vitB2 = 0.15, vitB3 = 1.3, vitC = 0.0, vitE = 1.85)
    )

    val defaultGoal = NutritionGoalEntity()
}
