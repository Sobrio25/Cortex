package com.aiagents.app.data.local

import androidx.room.*
import com.aiagents.app.data.model.AgentEntity
import com.aiagents.app.data.model.CommandPermissionEntity
import com.aiagents.app.data.model.FileEntity
import com.aiagents.app.data.model.MessageEntity
import com.aiagents.app.data.model.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents WHERE isSystemAgent = 0 ORDER BY updatedAt DESC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents ORDER BY updatedAt DESC")
    fun getAllAgentsIncludingSystem(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getAgentById(id: Long): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity): Long

    @Update
    suspend fun updateAgent(agent: AgentEntity)

    @Delete
    suspend fun deleteAgent(agent: AgentEntity)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteAgentById(id: Long)

    @Query("SELECT * FROM agents WHERE name = :name LIMIT 1")
    suspend fun getAgentByName(name: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE role = :role LIMIT 1")
    suspend fun getAgentByRole(role: String): AgentEntity?

    @Query("SELECT * FROM agents")
    suspend fun getAllAgentsOnce(): List<AgentEntity>
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY updatedAt DESC")
    fun getAllWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getWorkspaceById(id: Long): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspace(workspace: WorkspaceEntity): Long

    @Update
    suspend fun updateWorkspace(workspace: WorkspaceEntity)

    @Delete
    suspend fun deleteWorkspace(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspaceById(id: Long)

    @Query("UPDATE workspaces SET activeAgentId = :agentId, updatedAt = :updatedAt WHERE id = :workspaceId")
    suspend fun setActiveAgent(workspaceId: Long, agentId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workspaces SET selectedModel = :model, updatedAt = :updatedAt WHERE id = :workspaceId")
    suspend fun setSelectedModel(workspaceId: Long, model: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workspaces SET selectedModel = '', updatedAt = :updatedAt WHERE selectedModel LIKE :providerPrefix || '%'")
    suspend fun clearSelectedModelsForProvider(
        providerPrefix: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE workspaces SET externalStorageUri = :uri, updatedAt = :updatedAt WHERE id = :workspaceId")
    suspend fun setExternalStorageUri(workspaceId: Long, uri: String?, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE workspaceId = :workspaceId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForWorkspace(workspaceId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE workspaceId = :workspaceId AND role IN ('USER', 'ASSISTANT') ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversationMessagesForWorkspace(workspaceId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role IN ('USER', 'ASSISTANT') ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversationMessages(conversationId: Long, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE workspaceId = :workspaceId")
    suspend fun deleteMessagesForWorkspace(workspaceId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND role = 'SYSTEM' AND content LIKE :checkpointPrefix || '%'")
    suspend fun deleteContextCheckpointsForConversation(conversationId: Long, checkpointPrefix: String)

    @Query("DELETE FROM messages WHERE workspaceId = :workspaceId AND conversationId IS NULL AND role = 'SYSTEM' AND content LIKE :checkpointPrefix || '%'")
    suspend fun deleteContextCheckpointsForWorkspace(workspaceId: Long, checkpointPrefix: String)

    @Transaction
    suspend fun saveContextCheckpoint(
        workspaceId: Long,
        conversationId: Long?,
        checkpointPrefix: String,
        checkpoint: MessageEntity
    ): Long {
        if (conversationId != null) {
            deleteContextCheckpointsForConversation(conversationId, checkpointPrefix)
        } else {
            deleteContextCheckpointsForWorkspace(workspaceId, checkpointPrefix)
        }
        return insertMessage(checkpoint)
    }
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE workspaceId = :workspaceId ORDER BY uploadedAt DESC")
    fun getFilesForWorkspace(workspaceId: Long): Flow<List<FileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Delete
    suspend fun deleteFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Long): FileEntity?
}

@Dao
interface CommandPermissionDao {
    @Query("SELECT * FROM command_permissions ORDER BY grantedAt DESC")
    fun getAllPermissions(): Flow<List<CommandPermissionEntity>>

    @Query("SELECT * FROM command_permissions WHERE commandPattern = :pattern LIMIT 1")
    suspend fun getPermissionByPattern(pattern: String): CommandPermissionEntity?

    @Query("SELECT * FROM command_permissions")
    suspend fun getAllPermissionsOnce(): List<CommandPermissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(permission: CommandPermissionEntity): Long

    @Update
    suspend fun update(permission: CommandPermissionEntity)

    @Query("UPDATE command_permissions SET permissionLevel = :level WHERE id = :id")
    suspend fun updatePermissionLevel(id: Long, level: String)

    @Query("UPDATE command_permissions SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long)

    @Query("DELETE FROM command_permissions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM command_permissions WHERE commandPattern = :pattern")
    suspend fun deleteByPattern(pattern: String)

    @Query("DELETE FROM command_permissions")
    suspend fun deleteAll()
}
