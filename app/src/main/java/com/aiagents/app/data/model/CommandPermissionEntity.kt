package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_permissions")
data class CommandPermissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandPattern: String,
    val permissionLevel: String,
    val grantedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): CommandPermission = CommandPermission(
        id = id,
        commandPattern = commandPattern,
        permissionLevel = PermissionLevel.valueOf(permissionLevel),
        grantedAt = grantedAt,
        lastUsedAt = lastUsedAt
    )

    companion object {
        fun fromDomain(permission: CommandPermission): CommandPermissionEntity = CommandPermissionEntity(
            id = permission.id,
            commandPattern = permission.commandPattern,
            permissionLevel = permission.permissionLevel.name,
            grantedAt = permission.grantedAt,
            lastUsedAt = permission.lastUsedAt
        )
    }
}

data class CommandPermission(
    val id: Long = 0,
    val commandPattern: String,
    val permissionLevel: PermissionLevel,
    val grantedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

enum class PermissionLevel {
    ALLOWED_ONCE,
    ALLOWED_ALWAYS,
    BLOCKED
}
