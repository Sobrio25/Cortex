package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiagents.app.data.model.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE conversationId = :conversationId ORDER BY position ASC")
    fun observeTodos(conversationId: Long): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE conversationId = :conversationId ORDER BY position ASC")
    suspend fun getTodos(conversationId: Long): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoEntity>)

    @Query("DELETE FROM todos WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: Long)

    @Query("UPDATE todos SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE todos SET status = 'completed', updatedAt = :now WHERE conversationId = :conversationId AND status = 'in_progress'")
    suspend fun completeInProgressForConversation(
        conversationId: Long,
        now: Long = System.currentTimeMillis()
    ): Int
}
