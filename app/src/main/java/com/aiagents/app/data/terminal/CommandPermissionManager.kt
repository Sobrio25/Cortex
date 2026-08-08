package com.aiagents.app.data.terminal

import com.aiagents.app.data.local.CommandPermissionDao
import com.aiagents.app.data.model.CommandPermission
import com.aiagents.app.data.model.CommandPermissionEntity
import com.aiagents.app.data.model.PermissionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionRequest(
    val command: String,
    val riskLevel: CommandRiskLevel,
    val baseCommand: String
)

data class PermissionDecision(
    val command: String,
    val permissionLevel: PermissionLevel
)

@Singleton
class CommandPermissionManager @Inject constructor(
    private val commandPermissionDao: CommandPermissionDao,
    private val shellExecutor: ShellExecutor
) {
    fun getAllPermissions(): Flow<List<CommandPermission>> {
        return commandPermissionDao.getAllPermissions().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun checkPermission(command: String): PermissionCheckResult {
        val baseCommand = extractBaseCommand(command)
        val riskLevel = shellExecutor.getCommandRiskLevel(command)
        
        if (riskLevel == CommandRiskLevel.BLOCKED) {
            return PermissionCheckResult.Blocked(command, "Comando bloqueado por políticas de seguridad")
        }
        
        // Primero buscar coincidencia exacta
        val exactMatch = commandPermissionDao.getPermissionByPattern(command)
        if (exactMatch != null) {
            return when (exactMatch.permissionLevel) {
                PermissionLevel.ALLOWED_ALWAYS.name -> {
                    commandPermissionDao.updateLastUsed(exactMatch.id, System.currentTimeMillis())
                    PermissionCheckResult.Allowed(command, PermissionLevel.ALLOWED_ALWAYS)
                }
                PermissionLevel.ALLOWED_ONCE.name -> {
                    commandPermissionDao.deleteById(exactMatch.id)
                    PermissionCheckResult.NeedsConfirmation(
                        PermissionRequest(command, riskLevel, baseCommand),
                        "Permitido una vez anteriormente, requiere confirmación nuevamente"
                    )
                }
                PermissionLevel.BLOCKED.name -> {
                    PermissionCheckResult.Blocked(command, "Comando previamente bloqueado")
                }
                else -> PermissionCheckResult.NeedsConfirmation(
                    PermissionRequest(command, riskLevel, baseCommand),
                    null
                )
            }
        }
        
        // Buscar coincidencias de glob en memoria (patrones con * y ? en cualquier posición).
        // Gana el patrón más específico (más largo) para que `git commit *` prime sobre `git *`.
        val allPermissions = commandPermissionDao.getAllPermissionsOnce()
        val globMatch = allPermissions
            .filter { matchesGlob(it.commandPattern, command) }
            .maxByOrNull { it.commandPattern.length }

        if (globMatch != null) {
            return when (globMatch.permissionLevel) {
                PermissionLevel.ALLOWED_ALWAYS.name -> {
                    commandPermissionDao.updateLastUsed(globMatch.id, System.currentTimeMillis())
                    PermissionCheckResult.Allowed(command, PermissionLevel.ALLOWED_ALWAYS)
                }
                PermissionLevel.ALLOWED_ONCE.name -> {
                    commandPermissionDao.deleteById(globMatch.id)
                    PermissionCheckResult.NeedsConfirmation(
                        PermissionRequest(command, riskLevel, baseCommand),
                        "Permitido una vez anteriormente, requiere confirmación nuevamente"
                    )
                }
                PermissionLevel.BLOCKED.name -> {
                    PermissionCheckResult.Blocked(command, "Comando previamente bloqueado")
                }
                else -> PermissionCheckResult.NeedsConfirmation(
                    PermissionRequest(command, riskLevel, baseCommand),
                    null
                )
            }
        }
        
        // Los comandos seguros (cat, ls, etc.) se permiten automáticamente
        // sin necesidad de confirmación, como en Kimi CLI o Claude Code
        if (riskLevel == CommandRiskLevel.SAFE) {
            return PermissionCheckResult.Allowed(command, PermissionLevel.ALLOWED_ALWAYS)
        }
        
        return PermissionCheckResult.NeedsConfirmation(
            PermissionRequest(command, riskLevel, baseCommand),
            "Comando con riesgo moderado, requiere autorización"
        )
    }
    
    suspend fun grantPermission(command: String, permissionLevel: PermissionLevel): PermissionDecision {
        val baseCommand = extractBaseCommand(command)
        val pattern = when (permissionLevel) {
            // Tanto "siempre permitir" como "bloquear siempre" se guardan como glob
            // sobre el comando base para cubrir toda la familia (ej. `git *`, `rm *`).
            PermissionLevel.ALLOWED_ALWAYS, PermissionLevel.BLOCKED -> "$baseCommand*"
            PermissionLevel.ALLOWED_ONCE -> command
        }
        
        val existing = commandPermissionDao.getPermissionByPattern(pattern)
        if (existing != null) {
            commandPermissionDao.updatePermissionLevel(existing.id, permissionLevel.name)
        } else {
            commandPermissionDao.insert(
                CommandPermissionEntity(
                    commandPattern = pattern,
                    permissionLevel = permissionLevel.name
                )
            )
        }
        
        return PermissionDecision(command, permissionLevel)
    }
    
    suspend fun revokePermission(commandPattern: String) {
        commandPermissionDao.deleteByPattern(commandPattern)
    }
    
    suspend fun clearAllPermissions() {
        commandPermissionDao.deleteAll()
    }
    
    private fun extractBaseCommand(command: String): String {
        val trimmed = command.trim()
        val parts = trimmed.split(Regex("\\s+"))
        return parts.firstOrNull() ?: trimmed
    }

    /** Matches a command against a stored glob pattern: `*` = cualquier secuencia, `?` = un carácter. */
    private fun matchesGlob(pattern: String, command: String): Boolean {
        val regex = buildString {
            append('^')
            var i = 0
            while (i < pattern.length) {
                when (val c = pattern[i]) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(c.toString()))
                }
                i++
            }
            append('$')
        }.toRegex()
        return regex.matches(command)
    }
}

sealed class PermissionCheckResult {
    data class Allowed(
        val command: String,
        val permissionLevel: PermissionLevel
    ) : PermissionCheckResult()
    
    data class NeedsConfirmation(
        val request: PermissionRequest,
        val message: String?
    ) : PermissionCheckResult()
    
    data class Blocked(
        val command: String,
        val reason: String
    ) : PermissionCheckResult()
}
