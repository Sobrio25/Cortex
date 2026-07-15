package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.model.AgentEntity
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

data class AgentCreatorResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class AgentCreatorToolHandler @Inject constructor(
    private val agentDao: AgentDao
) {
    companion object {
        private const val TAG = "AgentCreatorToolHandler"
        const val TOOL_CREATE = "create_agent"
        const val TOOL_DELETE = "delete_agent"

        val ALL_TOOL_NAMES = setOf(TOOL_CREATE, TOOL_DELETE)

        private fun param(type: String, desc: String) = mapOf("type" to type, "description" to desc)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_CREATE,
                    "description" to "Create a persistent custom AI agent only when the user explicitly asks to create one. Ordinary task delegation uses temporary subagents instead.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to param("string", "Agent name (short, 1-2 words). Must be unique."),
                            "role" to param("string", "Short role description (e.g. 'Legal Advisor', 'Chef')"),
                            "systemPrompt" to param("string", "Full system prompt. Must include language rule and structured sections."),
                            "whenToUse" to param("string", "CSV of keywords (Spanish+English) for delegation routing by the main assistant"),
                            "temperature" to param("number", "0.0-1.0. Low=precise, High=creative. Default: 0.5"),
                            "maxTokens" to param("integer", "Max response tokens. Default: 8192"),
                            "enableTerminal" to param("boolean", "Allow shell commands. Default: false"),
                            "sarcasmLevel" to param("integer", "0-100. Default: 0"),
                            "creativityLevel" to param("integer", "0-100. Default: 50"),
                            "formalityLevel" to param("integer", "0-100. Default: 50"),
                            "empathyLevel" to param("integer", "0-100. Default: 50"),
                            "technicalPrecision" to param("integer", "0-100. Default: 70")
                        ),
                        "required" to listOf("name", "role", "systemPrompt", "whenToUse")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_DELETE,
                    "description" to "Delete an existing agent by name. CRITICAL: You MUST ALWAYS ask the user for explicit confirmation before calling this tool. Use <ask_options> to confirm. Never delete the main assistant or other system agents.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to param("string", "Exact name of the agent to delete. Case-sensitive.")
                        ),
                        "required" to listOf("name")
                    )
                )
            )
        )
    }

    suspend fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String,
        allowUserRequestedCreation: Boolean = false
    ): AgentCreatorResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_CREATE -> if (allowUserRequestedCreation) {
                    createAgent(toolCallId, args)
                } else {
                    AgentCreatorResult(
                        toolCallId,
                        false,
                        "La creación de agentes persistentes requiere una solicitud explícita del usuario. Usa spawn_subagents sin agent_name para trabajo temporal."
                    )
                }
                TOOL_DELETE -> deleteAgent(toolCallId, args)
                else -> AgentCreatorResult(toolCallId, false, "Tool desconocido: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            AgentCreatorResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private suspend fun createAgent(toolCallId: String, args: com.google.gson.JsonObject): AgentCreatorResult {
        val name = args.get("name")?.asString
            ?: return AgentCreatorResult(toolCallId, false, "Error: 'name' es requerido")
        val role = args.get("role")?.asString
            ?: return AgentCreatorResult(toolCallId, false, "Error: 'role' es requerido")
        val systemPrompt = args.get("systemPrompt")?.asString
            ?: return AgentCreatorResult(toolCallId, false, "Error: 'systemPrompt' es requerido")
        val whenToUse = args.get("whenToUse")?.asString
            ?: return AgentCreatorResult(toolCallId, false, "Error: 'whenToUse' es requerido")

        // Check for duplicate name
        val existing = agentDao.getAgentByName(name)
        if (existing != null) {
            return AgentCreatorResult(toolCallId, false, "Error: Ya existe un agente con el nombre '$name'")
        }

        val temperature = args.get("temperature")?.asFloat ?: 0.5f
        val maxTokens = args.get("maxTokens")?.asInt ?: 8192
        val enableTerminal = args.get("enableTerminal")?.asBoolean ?: false
        val sarcasmLevel = args.get("sarcasmLevel")?.asInt ?: 0
        val creativityLevel = args.get("creativityLevel")?.asInt ?: 50
        val formalityLevel = args.get("formalityLevel")?.asInt ?: 50
        val empathyLevel = args.get("empathyLevel")?.asInt ?: 50
        val technicalPrecision = args.get("technicalPrecision")?.asInt ?: 70

        val now = System.currentTimeMillis()
        val entity = AgentEntity(
            name = name,
            role = role,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            folderPath = "agents/${name.lowercase().replace(" ", "_")}",
            enableTerminal = enableTerminal,
            whenToUse = whenToUse,
            createdAt = now,
            updatedAt = now,
            sarcasmLevel = sarcasmLevel,
            creativityLevel = creativityLevel,
            formalityLevel = formalityLevel,
            empathyLevel = empathyLevel,
            technicalPrecision = technicalPrecision,
            isSystemAgent = false
        )

        val id = agentDao.insertAgent(entity)
        Log.d(TAG, "Created agent '$name' with ID $id")

        return AgentCreatorResult(toolCallId, true,
            "Agente creado exitosamente:\n" +
            "- **Nombre**: $name\n" +
            "- **Rol**: $role\n" +
            "- **ID**: $id\n" +
            "- **Temperature**: $temperature\n" +
            "- **Keywords**: $whenToUse\n" +
            "- **Personalidad**: sarcasm=$sarcasmLevel, creativity=$creativityLevel, formality=$formalityLevel, empathy=$empathyLevel, precision=$technicalPrecision\n\n" +
            "El agente ya está disponible en la lista de agentes y el asistente principal puede delegarle tareas."
        )
    }

    private suspend fun deleteAgent(toolCallId: String, args: com.google.gson.JsonObject): AgentCreatorResult {
        val name = args.get("name")?.asString
            ?: return AgentCreatorResult(toolCallId, false, "Error: 'name' es requerido")

        val existing = agentDao.getAgentByName(name)
            ?: return AgentCreatorResult(toolCallId, false, "Error: No existe un agente con el nombre '$name'")

        if (existing.isSystemAgent) {
            return AgentCreatorResult(toolCallId, false, "Error: No se puede eliminar '$name' porque es un agente del sistema.")
        }

        agentDao.deleteAgentById(existing.id)
        Log.d(TAG, "Deleted agent '$name' (ID ${existing.id})")

        return AgentCreatorResult(toolCallId, true,
            "Agente eliminado exitosamente:\n" +
            "- **Nombre**: $name\n" +
            "- **Rol**: ${existing.role}\n" +
            "- **ID**: ${existing.id}\n\n" +
            "El agente ya no aparece en la lista ni esta disponible para delegación."
        )
    }
}
