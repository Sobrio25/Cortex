package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.domain.model.AgentRoles
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

data class AgentSelectionResult(
    val toolCallId: String,
    val success: Boolean,
    val agentName: String,
    val reason: String,
    val confidence: String // "high", "medium", "low"
)

@Singleton
class AgentSelectionToolHandler @Inject constructor(
    private val agentDao: AgentDao
) {
    companion object {
        private const val TAG = "AgentSelectionToolHandler"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "select_agent",
                        "description" to "Select the most appropriate agent for a task based on its description and agent capabilities.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "task_description" to mapOf(
                                    "type" to "string",
                                    "description" to "Descripción de la tarea para la cual se necesita seleccionar el mejor agente"
                                )
                            ),
                            "required" to listOf("task_description")
                        )
                    )
                )
            )
        }
    }

    private val gson = Gson()

    suspend fun executeTool(toolCallId: String, arguments: String): AgentSelectionResult {
        val orchestratorName = try {
            agentDao.getAgentByRole(AgentRoles.ORCHESTRATOR)?.name ?: "Orchestrator"
        } catch (_: Exception) {
            "Orchestrator"
        }
        return try {
            val args = gson.fromJson(arguments, JsonObject::class.java) ?: JsonObject()
            val taskDescription = args.get("task_description")?.asString
                ?: return AgentSelectionResult(
                    toolCallId = toolCallId,
                    success = false,
                    agentName = orchestratorName,
                    reason = "Parámetro 'task_description' requerido",
                    confidence = "low"
                )

            selectBestAgent(toolCallId, taskDescription, orchestratorName)
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting agent", e)
            AgentSelectionResult(
                toolCallId = toolCallId,
                success = false,
                agentName = orchestratorName,
                reason = "Error al seleccionar agente: ${e.message}",
                confidence = "low"
            )
        }
    }

    private suspend fun selectBestAgent(
        toolCallId: String,
        taskDescription: String,
        orchestratorName: String
    ): AgentSelectionResult {
        val agents = agentDao.getAllAgentsOnce()
            .filter { it.role != AgentRoles.ORCHESTRATOR && it.whenToUse.isNotEmpty() }
            .map { it.toDomain() }

        if (agents.isEmpty()) {
            return AgentSelectionResult(
                toolCallId = toolCallId,
                success = true,
                agentName = orchestratorName,
                reason = "No hay agentes especializados disponibles",
                confidence = "high"
            )
        }

        val taskWords = normalizeText(taskDescription).split("\\s+".toRegex()).toSet()

        var bestAgent: String = orchestratorName
        var bestScore = 0
        var bestReason = "Ningún agente especializado coincide con la tarea"

        for (agent in agents) {
            val whenToUseWords = normalizeText(agent.whenToUse).split("\\s+".toRegex()).toSet()
            val roleWords = normalizeText(agent.role).split("\\s+".toRegex()).toSet()
            val nameWords = normalizeText(agent.name).split("\\s+".toRegex()).toSet()

            val allAgentWords = whenToUseWords + roleWords + nameWords

            // Count matching keywords
            val matchingWords = taskWords.intersect(allAgentWords)
            val score = matchingWords.size

            // Bonus for phrase matches in whenToUse
            val phraseBonus = if (containsPhrase(agent.whenToUse, taskDescription)) 3 else 0
            val totalScore = score + phraseBonus

            if (totalScore > bestScore) {
                bestScore = totalScore
                bestAgent = agent.name
                bestReason = "Coincidencia con: ${agent.role}. whenToUse: ${agent.whenToUse}"
            }
        }

        val confidence = when {
            bestScore >= 5 -> "high"
            bestScore >= 2 -> "medium"
            bestScore >= 1 -> "low"
            else -> "low"
        }

        return AgentSelectionResult(
            toolCallId = toolCallId,
            success = true,
            agentName = bestAgent,
            reason = bestReason,
            confidence = confidence
        )
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-záéíóúñü0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsPhrase(whenToUse: String, taskDescription: String): Boolean {
        val normalizedWhen = normalizeText(whenToUse)
        val normalizedTask = normalizeText(taskDescription)

        // Check if any 2+ word phrase from whenToUse appears in taskDescription
        val whenWords = normalizedWhen.split(" ")
        for (i in 0 until whenWords.size - 1) {
            val phrase = "${whenWords[i]} ${whenWords[i + 1]}"
            if (phrase.length > 5 && normalizedTask.contains(phrase)) {
                return true
            }
        }
        return false
    }

    fun formatResultForLLM(result: AgentSelectionResult): String {
        return buildString {
            append("{\n")
            append("  \"agent_name\": \"${result.agentName}\",\n")
            append("  \"reason\": \"${result.reason}\",\n")
            append("  \"confidence\": \"${result.confidence}\"\n")
            append("}")
        }
    }
}
