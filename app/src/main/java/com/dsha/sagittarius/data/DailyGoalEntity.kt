package com.dsha.sagittarius.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 当日目标：每天自动重置，勾选后只在完成当天给营养结构加一笔虚拟摄入。
 * nutrientKey 取 water/protein/carbs/fat/fiber/calories/sodium。
 */
@Entity(tableName = "daily_goals")
data class DailyGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val nutrientKey: String,
    val amount: Double,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** yyyy-MM-dd；等于今天才算完成，次日自动回到未完成。 */
    val completedDate: String? = null
)
