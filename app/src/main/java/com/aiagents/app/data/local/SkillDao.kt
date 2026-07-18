package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiagents.app.data.model.SkillEntity
import com.aiagents.app.data.model.SkillReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query(
        """
        SELECT * FROM skills
        ORDER BY
            CASE status WHEN 'ACTIVE' THEN 0 WHEN 'INACTIVE' THEN 1 WHEN 'DRAFT' THEN 2 ELSE 3 END,
            isImmutable DESC,
            name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE status = 'ACTIVE' ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE status = 'ACTIVE' ORDER BY isImmutable DESC, name COLLATE NOCASE ASC")
    suspend fun getActiveOnce(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SkillEntity?

    @Query("SELECT * FROM skills WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): SkillEntity?

    @Query("SELECT * FROM skills WHERE origin = 'AUTO' AND name COLLATE NOCASE = :name LIMIT 1")
    suspend fun getAutomaticByName(name: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(skill: SkillEntity): Long

    @Query(
        """
        UPDATE skills SET
            name = :name,
            description = :description,
            whenToUse = :whenToUse,
            instructions = :instructions,
            category = :category,
            requiredTools = :requiredTools,
            version = version + 1,
            updatedAt = :updatedAt
        WHERE id = :id AND isImmutable = 0
        """
    )
    suspend fun updateMutable(
        id: Long,
        name: String,
        description: String,
        whenToUse: String,
        instructions: String,
        category: String,
        requiredTools: String,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE skills SET
            name = :name,
            description = :description,
            whenToUse = :whenToUse,
            instructions = :instructions,
            category = :category,
            requiredTools = :requiredTools,
            status = 'ACTIVE',
            version = version + 1,
            updatedAt = :updatedAt,
            activatedAt = COALESCE(activatedAt, :updatedAt),
            archivedAt = NULL
        WHERE id = :id
          AND origin = 'AUTO'
          AND isImmutable = 0
          AND status != 'ARCHIVED'
        """
    )
    suspend fun updateAutomatic(
        id: Long,
        name: String,
        description: String,
        whenToUse: String,
        instructions: String,
        category: String,
        requiredTools: String,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE skills SET
            status = :status,
            updatedAt = :updatedAt,
            activatedAt = :activatedAt,
            archivedAt = :archivedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        updatedAt: Long,
        activatedAt: Long?,
        archivedAt: Long?
    ): Int
}

@Dao
interface SkillReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(review: SkillReviewEntity): Long

    @Query("SELECT * FROM skill_reviews WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SkillReviewEntity?

    @Query("SELECT * FROM skill_reviews ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SkillReviewEntity>>

    @Query(
        """
        UPDATE skill_reviews SET
            status = :status,
            summary = :summary,
            candidateSkillId = :candidateSkillId,
            redactedTranscript = '',
            completedAt = :completedAt
        WHERE id = :id AND status = 'PENDING'
        """
    )
    suspend fun complete(
        id: Long,
        status: String,
        summary: String,
        candidateSkillId: Long?,
        completedAt: Long
    ): Int
}
