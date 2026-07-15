package com.aiagents.app.data.terminal

import com.aiagents.app.data.events.AgentChangeNotifier
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
    private val repository: SkillRepository,
    private val changeNotifier: AgentChangeNotifier
) {
    companion object {
        const val TOOL_CREATE = "skill_create"
        const val TOOL_LIST = "skill_list"
        const val TOOL_VIEW = "skill_view"
        const val TOOL_ACTIVATE = "skill_activate"
        const val TOOL_ARCHIVE = "skill_archive"
        val ALL_TOOL_NAMES = setOf(TOOL_CREATE, TOOL_LIST, TOOL_VIEW)

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
                "Busca skills instaladas y devuelve solo metadatos. Usa skill_view antes de aplicar una skill.",
                mapOf(
                    "query" to string("Texto opcional para buscar por nombre, descripción o señales de uso"),
                    "status" to mapOf("type" to "string", "enum" to listOf("DRAFT", "ACTIVE", "ARCHIVED", "ALL")),
                    "limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 50)
                ),
                emptyList()
            ),
            function(
                TOOL_VIEW,
                "Carga las instrucciones completas de una skill por ID o slug. Las skills no activas son solo para inspección.",
                mapOf("id_or_slug" to string("ID numérico o slug exacto de la skill")),
                listOf("id_or_slug")
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
            TOOL_VIEW -> view(toolCallId, args)
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
                changeNotifier.skillCreated(input.name)
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
        val query = args.get("query")?.asString?.trim().orEmpty()
        val limit = args.get("limit")?.asInt?.coerceIn(1, 50) ?: 20
        val skills = repository.getSkillsOnce()
            .asSequence()
            .filter { status == null || it.status == status }
            .filter { skill ->
                query.isBlank() || listOf(
                    skill.name,
                    skill.slug,
                    skill.description,
                    skill.whenToUse
                ).any { it.contains(query, ignoreCase = true) }
            }
            .take(limit)
            .toList()
        val content = if (skills.isEmpty()) {
            "No hay skills para el filtro $requested."
        } else {
            skills.joinToString("\n") {
                "- #${it.id} ${it.slug}: ${it.name} [${it.status}] (${it.origin}) — ${it.description}"
            }
        }
        return SkillToolResult(toolCallId, TOOL_LIST, true, content)
    }

    private suspend fun view(toolCallId: String, args: JsonObject): SkillToolResult {
        val key = args.get("id_or_slug")?.asString?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return missing(toolCallId, TOOL_VIEW, "id_or_slug")
        val skill = key.toLongOrNull()?.let { repository.getSkill(it) }
            ?: repository.getSkillBySlug(key)
            ?: return SkillToolResult(toolCallId, TOOL_VIEW, false, "No existe una skill con ID o slug '$key'.")
        val availability = if (skill.status == SkillStatus.ACTIVE) {
            "ACTIVE: puedes aplicar estas instrucciones cuando coincidan con la tarea."
        } else {
            "${skill.status}: solo inspección; no apliques estas instrucciones hasta que la skill esté ACTIVE."
        }
        val content = buildString {
            appendLine("# ${skill.name}")
            appendLine("Slug: ${skill.slug}")
            appendLine("Status: $availability")
            appendLine("Description: ${skill.description}")
            appendLine("When to use: ${skill.whenToUse}")
            appendLine()
            append(skill.instructions)
        }
        return SkillToolResult(toolCallId, TOOL_VIEW, true, content)
    }

    private fun missing(toolCallId: String, toolName: String, field: String) =
        SkillToolResult(toolCallId, toolName, false, "Falta el parámetro '$field'.")
}
