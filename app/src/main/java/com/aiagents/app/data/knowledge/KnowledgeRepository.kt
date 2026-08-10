package com.aiagents.app.data.knowledge

import com.aiagents.app.data.local.KnowledgeDao
import com.aiagents.app.data.model.KnowledgeChunkEntity
import com.aiagents.app.data.model.KnowledgeDocumentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** A retrieval hit: one chunk from the knowledge base with its similarity score. */
data class KnowledgeHit(
    val documentId: Long,
    val chunkIndex: Int,
    val text: String,
    val score: Float
)

/**
 * On-device semantic RAG store: documents are chunked, embedded with the local
 * Text Embedder, and searched by cosine similarity over the query embedding.
 * Brute-force scan is fine for personal-scale knowledge bases (thousands of
 * chunks) and keeps the feature fully offline and dependency-free.
 */
@Singleton
class KnowledgeRepository @Inject constructor(
    private val dao: KnowledgeDao,
    private val embedderManager: TextEmbedderManager
) {

    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>> = dao.observeDocuments()

    suspend fun getDocumentsOnce(): List<KnowledgeDocumentEntity> = dao.getDocumentsOnce()

    suspend fun isModelReady(): Boolean = embedderManager.isModelReady()

    /**
     * Chunks + embeds + persists a document. Returns the number of chunks created.
     * Fails with a clear message when the embedding model is not downloaded.
     */
    suspend fun addDocument(title: String, text: String): Result<Int> = runCatching {
        require(embedderManager.isModelReady()) {
            "The embedding model is not downloaded. Open Settings > Knowledge Base and download it first."
        }
        val chunks = KnowledgeChunker.chunk(text)
        require(chunks.isNotEmpty()) { "The document has no usable text." }
        val now = System.currentTimeMillis()
        val embeddings = embedderManager.embed(chunks)
        val documentId = dao.insertDocument(
            KnowledgeDocumentEntity(
                title = title.trim().ifEmpty { "Untitled document" },
                sourceType = "PASTED",
                chunkCount = chunks.size,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertChunks(
            chunks.mapIndexed { index, chunk ->
                KnowledgeChunkEntity(
                    documentId = documentId,
                    chunkIndex = index,
                    text = chunk,
                    embedding = Embeddings.toBytes(embeddings[index])
                )
            }
        )
        chunks.size
    }

    /** Embeds the query and returns the top-k most similar chunks. */
    suspend fun search(query: String, limit: Int = 5): List<KnowledgeHit> {
        if (query.isBlank()) return emptyList()
        val allChunks = dao.getAllChunks()
        if (allChunks.isEmpty()) return emptyList()
        val queryEmbedding = embedderManager.embed(listOf(query.trim())).first()
        return allChunks
            .mapNotNull { chunk ->
                val score = Embeddings.cosine(queryEmbedding, Embeddings.toFloatArray(chunk.embedding))
                if (score.isNaN()) null else KnowledgeHit(
                    documentId = chunk.documentId,
                    chunkIndex = chunk.chunkIndex,
                    text = chunk.text,
                    score = score
                )
            }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 10))
    }

    suspend fun documentTitle(documentId: Long): String? = dao.titleFor(documentId)

    suspend fun deleteDocument(documentId: Long) = dao.deleteDocument(documentId)

    suspend fun totalChunks(): Int = dao.chunkCount()
}
