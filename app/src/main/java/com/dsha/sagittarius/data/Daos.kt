package com.dsha.sagittarius.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :start AND :end " +
            "ORDER BY date DESC, createdAt DESC"
    )
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :start AND :end " +
            "ORDER BY date DESC, createdAt DESC"
    )
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseEntity?

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("UPDATE expenses SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BudgetEntity?

    @Query(
        "SELECT * FROM budgets WHERE rangeType = :type AND startDate = :start " +
            "AND endDate = :end LIMIT 1"
    )
    suspend fun findExact(
        type: BudgetRangeType,
        start: LocalDate,
        end: LocalDate
    ): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM budgets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countExpenses(categoryId: Long): Int
}

@Dao
interface BudgetNodeDao {
    @Query(
        "SELECT * FROM budget_nodes WHERE startDate = :start AND endDate = :end " +
            "ORDER BY sortOrder ASC, id ASC"
    )
    fun observeForRange(start: LocalDate, end: LocalDate): Flow<List<BudgetNodeEntity>>

    @Query(
        "SELECT * FROM budget_nodes WHERE startDate = :start AND endDate = :end " +
            "ORDER BY sortOrder ASC, id ASC"
    )
    suspend fun getForRange(start: LocalDate, end: LocalDate): List<BudgetNodeEntity>

    @Query("SELECT * FROM budget_nodes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BudgetNodeEntity?

    @Insert
    suspend fun insert(node: BudgetNodeEntity): Long

    @Update
    suspend fun update(node: BudgetNodeEntity)

    @Query("DELETE FROM budget_nodes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE budget_nodes SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query("UPDATE budget_nodes SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)
}

@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 1 LIMIT 1")
    suspend fun get(): AppStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AppStateEntity)
}

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentForSession(sessionId: Long, limit: Int): List<ChatHistoryEntity>

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getAllForSession(sessionId: Long): List<ChatHistoryEntity>

    @Query("SELECT COUNT(*) FROM chat_history WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("DELETE FROM chat_history WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Insert
    suspend fun insert(item: ChatHistoryEntity): Long

    @Query("DELETE FROM chat_history")
    suspend fun deleteAll()
}

@Dao
interface CnFoodDao {
    @Query("SELECT COUNT(*) FROM cn_foods")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CnFoodEntity>)

    @Query(
        "SELECT * FROM cn_foods WHERE name LIKE '%' || :query || '%' " +
            "ORDER BY CASE WHEN name = :query THEN 0 WHEN name LIKE :query || '%' THEN 1 ELSE 2 END, id " +
            "LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 3): List<CnFoodEntity>

    @Query("SELECT * FROM cn_foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CnFoodEntity?
}

@Dao
interface ChatHistoryDeleteDao {
    @Query(
        "DELETE FROM chat_history WHERE id = (" +
            "SELECT id FROM chat_history WHERE sessionId = :sessionId AND role = :role AND content = :content " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1)"
    )
    suspend fun deleteLatest(sessionId: Long, role: String, content: String)
}


@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC, id DESC")
    suspend fun getAll(): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChatSessionEntity?

    @Insert
    suspend fun insert(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
