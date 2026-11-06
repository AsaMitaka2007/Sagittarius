package com.dsha.sagittarius.data

import androidx.annotation.ColorInt
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 金额一律以“分”为单位存储，避免 Double/Float 的精度问题。
 */
@Entity(
    tableName = "expenses",
    indices = [Index("date"), Index("categoryId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amountCents: Long,
    val date: LocalDate,
    val categoryId: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["rangeType", "startDate", "endDate"], unique = true)
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rangeType: BudgetRangeType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val amountCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 单行表。当前激活日期范围与悬浮窗开关单独持久化，
 * 悬浮窗、主界面、历史页、小部件都读取同一行。
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = 1,
    val activeRangeType: BudgetRangeType,
    val activeStartDate: LocalDate,
    val activeEndDate: LocalDate,
    val activeBudgetId: Long?,
    val floatingEnabled: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["systemKey"], unique = true),
        Index("parentId"),
        Index("type")
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val systemKey: String? = null,
    val name: String,
    val iconKey: String,
    @ColorInt val color: Int,
    val isCustom: Boolean,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val type: String = "expense",
    val parentId: Long? = null,
    val isSystem: Boolean = false
)

/**
 * 预算树的分配节点。parentId 为空表示根下的第一层；
 * 最多允许两层子节点，所有节点都绑定到具体日期范围。
 */
@Entity(
    tableName = "budget_nodes",
    indices = [
        Index("parentId"),
        Index("startDate", "endDate"),
        Index("categoryId")
    ]
)
data class BudgetNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val parentId: Long? = null,
    val budgetName: String,
    val amountCents: Long,
    val categoryId: Long? = null,
    val sortOrder: Int = 0,
    val rangeType: BudgetRangeType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Sagittarius 对话历史：只持久化最终用户/助手文本，推理与工具过程不落库。 */
@Entity(
    tableName = "chat_history",
    indices = [Index("createdAt"), Index("sessionId")]
)
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long = 1L,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
