package com.dsha.sagittarius.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * cn-food-mcp 的只读本地营养字典。
 * 与用户食材库 FoodEntity 完全隔离，不参与饮食轮盘，也不允许用户编辑。
 */
@Entity(
    tableName = "cn_foods",
    indices = [Index("name")]
)
data class CnFoodEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val category: String? = null,
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbohydrateG: Double = 0.0,
    val fiberG: Double = 0.0,
    val waterG: Double = 0.0,
    val ashG: Double = 0.0,
    val vitaminA: Double = 0.0,
    val carotene: Double = 0.0,
    val retinolEquivalent: Double = 0.0,
    val vitaminB1: Double = 0.0,
    val vitaminB2: Double = 0.0,
    val niacin: Double = 0.0,
    val vitaminC: Double = 0.0,
    val vitaminE: Double = 0.0,
    val potassium: Double = 0.0,
    val sodium: Double = 0.0,
    val calcium: Double = 0.0,
    val magnesium: Double = 0.0,
    val iron: Double = 0.0,
    val manganese: Double = 0.0,
    val zinc: Double = 0.0,
    val copper: Double = 0.0,
    val phosphorus: Double = 0.0,
    val selenium: Double = 0.0
)
