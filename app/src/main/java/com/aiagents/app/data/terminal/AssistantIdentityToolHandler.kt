package com.aiagents.app.data.terminal

import com.aiagents.app.data.identity.AssistantIdentityManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantIdentityToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class AssistantIdentityToolHandler @Inject constructor(
    private val identityManager: AssistantIdentityManager
) {
    suspend fun executeTool(toolCallId: String, arguments: String): AssistantIdentityToolResult {
        val requestedName = runCatching {
            Gson().fromJson(arguments, JsonObject::class.java)?.get("name")?.asString.orEmpty()
        }.getOrDefault("")
        if (requestedName.isBlank()) {
            return AssistantIdentityToolResult(toolCallId, false, "Falta el nuevo nombre del asistente.")
        }
        return identityManager.rename(requestedName).fold(
            onSuccess = { name ->
                AssistantIdentityToolResult(toolCallId, true, "El nombre del asistente ahora es $name.")
            },
            onFailure = { error ->
                AssistantIdentityToolResult(toolCallId, false, error.message ?: "No se pudo cambiar el nombre.")
            }
        )
    }

    companion object {
        const val TOOL_NAME = "set_assistant_name"

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to "Change the main assistant's configured name. Call only when the user explicitly asks to rename the assistant or tells you what your name should be.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to mapOf(
                                "type" to "string",
                                "description" to "The new assistant name, up to 40 characters."
                            )
                        ),
                        "required" to listOf("name")
                    )
                )
            )
        )
    }
}
