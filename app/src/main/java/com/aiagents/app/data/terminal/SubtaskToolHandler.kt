package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.remote.AIClientFactory
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

data class SubtaskResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

/**
 * Executes isolated programming subtasks with fresh context.
 * Inspired by OpenCode's "task" tool — each call is independent with no conversation history,
 * allowing complex tasks to be broken into focused parallel or sequential subtasks without timeout.
 */
@Singleton
class SubtaskToolHandler @Inject constructor(
    private val aiClientFactory: AIClientFactory,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "SubtaskToolHandler"
        const val TOOL_NAME = "execute_subtask"
        val ALL_TOOL_NAMES = setOf(TOOL_NAME)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to "Execute an isolated subtask with fresh context. SUPPORTS PARALLEL EXECUTION: call this tool multiple times in a single response and all calls will run simultaneously. Use for: (1) Parallel research — investigate multiple topics at once; (2) Complex programming — write multiple files/modules in parallel; (3) Any task that benefits from focused independent attention. Each call is fully isolated with no shared conversation history — include ALL needed context, requirements, and constraints in the 'context' field.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "task" to mapOf(
                                "type" to "string",
                                "description" to "Clear, specific description of what to implement or solve"
                            ),
                            "context" to mapOf(
                                "type" to "string",
                                "description" to "All relevant context: requirements, existing code snippets, file paths, tech stack, dependencies, interfaces, constraints. Be thorough — the subtask agent only sees this."
                            ),
                            "expected_output" to mapOf(
                                "type" to "string",
                                "description" to "What to return (e.g. 'complete file content ready to save', 'code implementation with explanation', 'list of steps', 'analysis'). Default: complete implementation."
                            )
                        ),
                        "required" to listOf("task", "context")
                    )
                )
            )
        )
    }

    /**
     * @param selectedModelKey Format: "PROVIDER|modelId" — same format used in WorkspaceDetailViewModel._selectedModel
     */
    suspend fun executeTool(
        toolCallId: String,
        arguments: String,
        selectedModelKey: String
    ): SubtaskResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val task = args.get("task")?.asString
                ?: return SubtaskResult(toolCallId, false, "Error: parámetro 'task' requerido")
            val context = args.get("context")?.asString ?: ""
            val expectedOutput = args.get("expected_output")?.asString ?: "complete implementation"

            Log.d(TAG, "Executing subtask: ${task.take(100)}")

            val (providerType, modelId) = parseModelKey(selectedModelKey)
                ?: return SubtaskResult(toolCallId, false, "Error: no se pudo determinar el proveedor/modelo activo del workspace")

            val (apiKey, baseUrl) = resolveCredentials(providerType)
                ?: return SubtaskResult(toolCallId, false, "Error: API key no configurada para $providerType")

            val systemPrompt = """You are a focused subtask executor for programming and technical work.
Complete the assigned task precisely and return $expectedOutput.
Be thorough, complete, and production-ready. Write full implementations without placeholders or TODOs.
Do not include meta-commentary or explanations unless specifically requested."""

            val userMessage = buildString {
                if (context.isNotBlank()) {
                    appendLine("## Context & Requirements")
                    appendLine(context)
                    appendLine()
                }
                appendLine("## Task")
                appendLine(task)
            }

            val client = aiClientFactory.createClient(providerType, apiKey, baseUrl)
            val result = client.chatWithTools(
                model = modelId,
                messages = listOf(ChatMessage(role = "user", content = userMessage)),
                systemPrompt = systemPrompt,
                temperature = 0.3f,
                maxTokens = 8192,
                tools = emptyList()
            )

            result.fold(
                onSuccess = { response ->
                    val output = response.content?.takeIf { it.isNotBlank() }
                        ?: "Subtarea completada (sin salida de texto)"
                    Log.d(TAG, "Subtask completed: ${output.take(100)}")
                    SubtaskResult(toolCallId, true, output)
                },
                onFailure = { e ->
                    Log.e(TAG, "Subtask API call failed", e)
                    SubtaskResult(toolCallId, false, "Error en subtarea: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing subtask", e)
            SubtaskResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private fun parseModelKey(key: String): Pair<ProviderType, String>? {
        if ("|" !in key) return null
        val providerStr = key.substringBefore("|")
        val modelId = key.substringAfter("|")
        val provider = runCatching { ProviderType.valueOf(providerStr) }.getOrNull() ?: return null
        return provider to modelId
    }

    private fun resolveCredentials(provider: ProviderType): Pair<String, String?>? {
        return when {
            provider == ProviderType.LOCAL || provider == ProviderType.OLLAMA -> {
                "" to securePreferences.getBaseUrl(provider)
            }
            provider == ProviderType.MOONSHOT -> {
                val keyPair = MoonshotEndpointType.entries
                    .mapNotNull { ep ->
                        val k = securePreferences.getMoonshotApiKey(ep)?.takeIf { it.isNotBlank() }
                        if (k != null) k to ep.baseUrl else null
                    }
                    .firstOrNull() ?: return null
                keyPair.first to keyPair.second
            }
            else -> {
                val apiKey = securePreferences.getApiKey(provider)?.takeIf { it.isNotBlank() } ?: return null
                apiKey to securePreferences.getBaseUrl(provider)
            }
        }
    }
}
