package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiagents.app.data.model.STTSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface STTSettingsDao {
    @Query("SELECT * FROM stt_settings WHERE workspaceId = :workspaceId LIMIT 1")
    suspend fun getSettingsForWorkspace(workspaceId: Int): STTSettingsEntity?

    @Query("SELECT * FROM stt_settings WHERE workspaceId = :workspaceId LIMIT 1")
    fun observeSettingsForWorkspace(workspaceId: Int): Flow<STTSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: STTSettingsEntity): Long

    @Update
    suspend fun updateSettings(settings: STTSettingsEntity)

    @Query("DELETE FROM stt_settings WHERE workspaceId = :workspaceId")
    suspend fun deleteSettingsForWorkspace(workspaceId: Int)
}
