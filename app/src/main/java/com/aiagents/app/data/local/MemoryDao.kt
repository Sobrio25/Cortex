package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aiagents.app.data.model.MemoryEntity
import com.aiagents.app.data.model.MemoryLinkEntity

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM cortex_memories WHERE id = :id")
    suspend fun getById(id: Long): MemoryEntity?

    @Query("DELETE FROM cortex_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT m.* FROM cortex_memories m
        JOIN cortex_memory_fts fts ON m.rowid = fts.rowid
        WHERE cortex_memory_fts MATCH :query AND m.confidence >= 0.2
        ORDER BY (m.confidence * m.importance) DESC, m.lastAccessedAt DESC
        LIMIT :limit
    """)
    suspend fun searchFts(query: String, limit: Int = 10): List<MemoryEntity>

    @Query("""
        SELECT * FROM cortex_memories
        WHERE category = :category
        ORDER BY importance DESC, updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getByCategory(category: String, limit: Int = 20): List<MemoryEntity>

    @Query("""
        SELECT * FROM cortex_memories
        ORDER BY importance DESC, updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getAll(limit: Int = 50): List<MemoryEntity>

    @Query("""
        SELECT * FROM cortex_memories
        ORDER BY updatedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecent(limit: Int = 20): List<MemoryEntity>

    @Query("""
        SELECT * FROM cortex_memories
        ORDER BY createdAt ASC
        LIMIT :limit
    """)
    suspend fun getOldest(limit: Int = 20): List<MemoryEntity>

    @Query("""
        SELECT * FROM cortex_memories
        WHERE category = :category AND subcategory = :subcategory
        ORDER BY updatedAt DESC
        LIMIT 5
    """)
    suspend fun getByCategoryAndSubcategory(category: String, subcategory: String): List<MemoryEntity>

    @Query("""
        UPDATE cortex_memories
        SET accessCount = accessCount + 1, lastAccessedAt = :now
        WHERE id = :id
    """)
    suspend fun incrementAccess(id: Long, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE cortex_memories
        SET confidence = confidence * :factor, updatedAt = :now
        WHERE lastAccessedAt < :threshold AND confidence > 0.1
    """)
    suspend fun decayOldMemories(threshold: Long, factor: Float = 0.9f, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM cortex_memories WHERE confidence < 0.1")
    suspend fun deleteWeakMemories()

    @Query("DELETE FROM cortex_memories WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpiredMemories(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cortex_memories")
    suspend fun count(): Int

    @Query("""
        DELETE FROM cortex_memories WHERE id IN (
            SELECT id FROM cortex_memories
            ORDER BY (confidence * importance) ASC, lastAccessedAt ASC
            LIMIT :count
        )
    """)
    suspend fun deleteLowestValue(count: Int)

    @Query("""
        DELETE FROM cortex_memories WHERE id IN (
            SELECT id FROM cortex_memories
            WHERE category = 'interaction' AND subcategory = 'conversation_summary'
            ORDER BY createdAt ASC
            LIMIT MAX(0, (
                SELECT COUNT(*) FROM cortex_memories
                WHERE category = 'interaction' AND subcategory = 'conversation_summary'
            ) - :maxToKeep)
        )
    """)
    suspend fun deleteOldestSummaries(maxToKeep: Int)

    @Query("""
        SELECT * FROM cortex_memories
        WHERE confidence >= :minConfidence
        ORDER BY importance DESC, lastAccessedAt DESC
        LIMIT :limit
    """)
    suspend fun getHighConfidence(minConfidence: Float, limit: Int): List<MemoryEntity>

    // --- Memory Links ---

    @Insert
    suspend fun insertLink(link: MemoryLinkEntity): Long

    @Query("""
        SELECT m.* FROM cortex_memories m
        JOIN cortex_memory_links l ON m.id = l.targetId
        WHERE l.sourceId = :memoryId
        ORDER BY l.strength DESC
    """)
    suspend fun getLinkedMemories(memoryId: Long): List<MemoryEntity>

    @Query("""
        SELECT * FROM cortex_memory_links
        WHERE sourceId = :memoryId OR targetId = :memoryId
    """)
    suspend fun getLinksForMemory(memoryId: Long): List<MemoryLinkEntity>

    @Query("""
        SELECT * FROM cortex_memory_links
        WHERE linkType = 'contradicts'
        AND (sourceId = :memoryId OR targetId = :memoryId)
    """)
    suspend fun getContradictions(memoryId: Long): List<MemoryLinkEntity>

    @Query("DELETE FROM cortex_memory_links WHERE sourceId = :memoryId OR targetId = :memoryId")
    suspend fun deleteLinksForMemory(memoryId: Long)

    @Query("DELETE FROM cortex_memories WHERE category = :category AND subcategory = :subcategory AND source = :source")
    suspend fun deleteByCategorySubcategorySource(category: String, subcategory: String, source: String)
}
