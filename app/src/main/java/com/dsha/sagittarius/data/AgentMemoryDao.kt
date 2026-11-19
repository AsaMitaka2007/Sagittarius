package com.dsha.sagittarius.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentMemoryDao {
    @Query("SELECT * FROM agent_memories ORDER BY category ASC, updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<AgentMemoryEntity>>

    @Query("SELECT * FROM agent_memories ORDER BY category ASC, updatedAt DESC, id DESC")
    suspend fun getAll(): List<AgentMemoryEntity>

    @Insert
    suspend fun insert(memory: AgentMemoryEntity): Long

    @Update
    suspend fun update(memory: AgentMemoryEntity)

    @Delete
    suspend fun delete(memory: AgentMemoryEntity)

    @Query("DELETE FROM agent_memories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
