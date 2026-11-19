package com.dsha.sagittarius.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 长期记忆分类。固定分类避免用户面对自由标签时无从下手。 */
object AgentMemoryCategory {
    const val PERSONA = "人设"
    const val DIET = "饮食习惯"
    const val BUDGET = "记账偏好"
    const val OTHER = "其他"

    val all = listOf(PERSONA, DIET, BUDGET, OTHER)

    fun normalize(value: String?): String {
        val text = value?.trim().orEmpty()
        return all.firstOrNull { it == text } ?: OTHER
    }
}

@Entity(
    tableName = "agent_memories",
    indices = [Index("category")]
)
data class AgentMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String = AgentMemoryCategory.OTHER,
    val content: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
