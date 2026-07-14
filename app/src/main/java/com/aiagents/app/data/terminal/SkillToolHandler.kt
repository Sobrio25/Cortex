package com.aiagents.app.data.terminal

import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.domain.model.SkillDraftInput
import com.aiagents.app.domain.model.SkillStatus
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

data class SkillToolResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String
)

@Singleton
class SkillToolHandler @Inject constructor(
    private val repository: SkillRepository
) {
    companion object {
        const val TOOL_CREATE = "skill_create"
        const val TOOL_LIST = "skill_list"
        const val TOOL_ACTIVATE = "skill_activate"
        const val TOOL_ARCHIVE = "skill_archive"
        val ALL_TOOL_NAMES = setOf(TOOL_CREATE, TOOL_LIST)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            function(
                TOOL_CREATE,
                "Crea una skill reutilizable en estado DRAFT cuando el usuario lo pide explícitamente. No incluye secretos y no la activa automáticamente.",
                mapOf(
                    "name" to string("Nombre breve de la skill"),
                    "description" to string("Resultado que produce"),
                    "when_to_use" to string("Señales concretas para usarla"),
                    "instructions" to string("Flujo, límites, errores y validación")
                ),
                listOf("name", "description", "when_to_use", "instructions")
            ),
            function(
                TOOL_LIST,
                "Lista skills y sus estados para que el usuario pueda revisarlas.",
                mapOf("status" to mapOf("type" to "string", "enum" to listOf("DRAFT", "ACTIVE", "ARCHIVED", "ALL"))),
                emptyList()
            )
        )

        private fun string(description: String): Map<String, Any> =
            mapOf("type" to "string", "description" to description)

        private fun function(
            name: String,
            description: String,
            properties: Map<String, Any>,
            required: List<String>
        ): Map<String, Any> = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to description,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to properties,
                    "required" to required
                )
            )
        )
    }

    suspend fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String,
        allowUserRequestedCreation: Boolean = false
    ): SkillToolResult {
        val args = runCatching { JsonParser.parseString(arguments).asJsonObject }
            .getOrElse { return SkillToolResult(toolCallId, toolName, false, "Argumentos JSON inválidos") }
        return when (toolName) {
            TOOL_CREATE -> if (allowUserRequestedCreation) {
                create(toolCallId, args)
            } else {
                SkillToolResult(
                    toolCallId,
                    TOOL_CREATE,
                    false,
                    "La creación requiere que el mensaje actual del usuario la pida explícitamente."
                )
            }
            TOOL_LIST -> list(toolCallId, args)
            TOOL_ACTIVATE, TOOL_ARCHIVE -> SkillToolResult(
                toolCallId,
                toolName,
                false,
                "La activación y el archivo requieren una acción directa del usuario en la pantalla Skills."
            )
            else -> SkillToolResult(toolCallId, toolName, false, "Tool de skills desconocida")
        }
    }

    private suspend fun create(toolCallId: String, args: JsonObject): SkillToolResult {
        fun required(name: String): String? = args.get(name)?.asString?.trim()?.takeIf { it.isNotBlank() }
        val input = SkillDraftInput(
            name = required("name") ?: return missing(toolCallId, TOOL_CREATE, "name"),
            description = required("description") ?: return missing(toolCallId, TOOL_CREATE, "description"),
            whenToUse = required("when_to_use") ?: return missing(toolCallId, TOOL_CREATE, "when_to_use"),
            instructions = required("instructions") ?: return missing(toolCallId, TOOL_CREATE, "instructions")
        )
        return repository.saveUserSkill(null, input).fold(
            onSuccess = { id ->
                SkillToolResult(
                    toolCallId,
                    TOOL_CREATE,
                    true,
                    "Skill creada como DRAFT con ID $id. El usuario puede revisarla y activarla desde Skills."
                )
            },
            onFailure = { SkillToolResult(toolCallId, TOOL_CREATE, false, it.message ?: "No se pudo crear la skill") }
        )
    }

    private suspend fun list(toolCallId: String, args: JsonObject): SkillToolResult {
        val requested = args.get("status")?.asString?.uppercase() ?: "ALL"
        val status = requested.takeUnless { it == "ALL" }?.let {
            runCatching { SkillStatus.valueOf(it) }.getOrNull()
                ?: return SkillToolResult(toolCallId, TOOL_LIST, false, "Estado inválido: $requested")
        }
        val skills = repository.getSkillsOnce().filter { status == null || it.status == status }
        val content = if (skills.isEmpty()) {
            "No hay skills para el filtro $requested."
        } else {
            skills.joinToString("\n") {
                "- #${it.id} ${it.name} [${it.status}] (${it.origin}) — ${it.description}"
            }
        }
        return SkillToolResult(toolCallId, TOOL_LIST, true, content)
    }

    private fun missing(toolCallId: String, toolName: String, field: String) =
        SkillToolResult(toolCallId, toolName, false, "Falta el parámetro '$field'.")
}
