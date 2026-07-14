package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiagents.app.data.model.DownloadProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadProgressDao {
    @Query("SELECT * FROM download_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadProgressEntity>>

    @Query("SELECT * FROM download_progress WHERE modelId = :modelId LIMIT 1")
    suspend fun get(modelId: String): DownloadProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DownloadProgressEntity)

    @Query("DELETE FROM download_progress WHERE modelId = :modelId")
    suspend fun delete(modelId: String)
}
