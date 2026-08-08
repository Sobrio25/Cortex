package com.aiagents.app.data.local

import androidx.room.*
import com.aiagents.app.data.model.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId AND parentConversationId IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun getConversationsForWorkspace(workspaceId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM conversations WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestConversation(workspaceId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE parentConversationId = :parentId ORDER BY createdAt ASC")
    fun getSubConversations(parentId: Long): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET selectedModelOverride = :model, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSelectedModelOverride(
        id: Long,
        model: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    // Memory extraction related queries
    
    /**
     * Gets conversations that need memory extraction, excluding the currently active one.
     * A conversation needs extraction if it has been updated since last extraction.
     */
    @Query("""
        SELECT * FROM conversations 
        WHERE id != :excludeConversationId 
        AND (lastMemoryExtraction IS NULL OR updatedAt > lastMemoryExtraction)
        AND status = 'active'
        AND contextKind = 'CHAT'
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getConversationsNeedingExtraction(
        excludeConversationId: Long?, 
        limit: Int = 10
    ): List<ConversationEntity>

    /**
     * Gets ALL conversations that need extraction (for app startup).
     */
    @Query("""
        SELECT * FROM conversations 
        WHERE (lastMemoryExtraction IS NULL OR updatedAt > lastMemoryExtraction)
        AND status = 'active'
        AND contextKind = 'CHAT'
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getAllConversationsNeedingExtraction(limit: Int = 20): List<ConversationEntity>

    /**
     * Updates the last memory extraction timestamp for a conversation.
     */
    @Query("UPDATE conversations SET lastMemoryExtraction = :timestamp WHERE id = :id")
    suspend fun updateLastMemoryExtraction(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Counts messages in a conversation since a specific timestamp.
     */
    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE conversationId = :conversationId 
        AND timestamp > :sinceTimestamp
    """)
    suspend fun countMessagesSince(conversationId: Long, sinceTimestamp: Long): Int
}
