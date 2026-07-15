package com.aiagents.app.data.identity

import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.domain.model.AgentRoles
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantIdentityManager @Inject constructor(
    private val agentDao: AgentDao,
    private val scheduledTaskDao: ScheduledTaskDao,
    private val securePreferences: SecurePreferences,
    private val profileStore: CortexProfileStore
) {
    fun configuredName(): String = securePreferences.getAssistantName() ?: DEFAULT_NAME

    suspend fun rename(requestedName: String): Result<String> = runCatching {
        val name = normalize(requestedName)
        require(name.isNotBlank()) { "El nombre del asistente no puede estar vacío." }
        require(name.length <= MAX_NAME_LENGTH) {
            "El nombre del asistente puede tener hasta $MAX_NAME_LENGTH caracteres."
        }

        val orchestrator = agentDao.getAgentByRole(AgentRoles.ORCHESTRATOR)
            ?: error("No se encontró el asistente principal.")
        val conflictingAgent = agentDao.getAgentByName(name)
        require(conflictingAgent == null || conflictingAgent.id == orchestrator.id) {
            "Ya existe otro agente llamado '$name'."
        }

        if (orchestrator.name != name) {
            val previousName = orchestrator.name
            agentDao.updateAgent(
                orchestrator.copy(name = name, updatedAt = System.currentTimeMillis())
            )
            scheduledTaskDao.renameAgent(previousName, name)
        }
        profileStore.renameIdentity(name)
        name
    }

    private fun normalize(value: String): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val DEFAULT_NAME = "Assistant"
        const val MAX_NAME_LENGTH = 40
    }
}
