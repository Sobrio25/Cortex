package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aiagents.app.data.model.KnowledgeChunkEntity
import com.aiagents.app.data.model.KnowledgeDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {

    @Insert
    suspend fun insertDocument(document: KnowledgeDocumentEntity): Long

    @Insert
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("SELECT * FROM knowledge_documents ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>>

    @Query("SELECT * FROM knowledge_documents ORDER BY updatedAt DESC")
    suspend fun getDocumentsOnce(): List<KnowledgeDocumentEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE documentId = :documentId ORDER BY chunkIndex")
    suspend fun getChunks(documentId: Long): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks")
    suspend fun getAllChunks(): List<KnowledgeChunkEntity>

    @Query("SELECT COUNT(*) FROM knowledge_chunks")
    suspend fun chunkCount(): Int

    @Query("SELECT title FROM knowledge_documents WHERE id = :documentId")
    suspend fun titleFor(documentId: Long): String?

    @Query("DELETE FROM knowledge_documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Long)

    @Query("UPDATE knowledge_documents SET chunkCount = :count, updatedAt = :now WHERE id = :documentId")
    suspend fun updateChunkCount(documentId: Long, count: Int, now: Long)
}
