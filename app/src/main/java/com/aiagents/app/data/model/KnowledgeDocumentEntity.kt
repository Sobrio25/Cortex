package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A document the user added to the on-device knowledge base (RAG source). */
@Entity(tableName = "knowledge_documents")
data class KnowledgeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceType: String, // PASTED | IMPORTED
    val chunkCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * A chunk of a knowledge-base document with its on-device semantic embedding
 * (float32 little-endian bytes, produced by the MediaPipe Text Embedder).
 */
@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = KnowledgeDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val text: String,
    val embedding: ByteArray
)
