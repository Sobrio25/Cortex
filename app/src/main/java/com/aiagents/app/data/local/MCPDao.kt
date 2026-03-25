package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiagents.app.data.model.MCPServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MCPDao {
    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<MCPServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getServerById(id: String): MCPServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: MCPServerEntity)

    @Update
    suspend fun updateServer(server: MCPServerEntity)

    @Query("UPDATE mcp_servers SET isEnabled = :enabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setServerEnabled(id: String, enabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE mcp_servers SET configJson = :configJson, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateServerConfig(id: String, configJson: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServer(id: String)
}
