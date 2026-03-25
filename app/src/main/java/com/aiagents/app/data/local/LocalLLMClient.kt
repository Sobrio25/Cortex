package com.aiagents.app.data.local

import android.content.Context
import com.aiagents.app.data.remote.AIClient
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.remote.ChatResponseWithTools
import com.aiagents.app.domain.model.LocalModel
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class LocalLLMClient(
    private val context: Context,
    private val modelRepository: LocalModelRepository
) : AIClient {
    
    private var llmInference: LlmInference? = null
    private var currentModel: String? = null

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            // Cargar el modelo si es necesario
            if (!loadModelIfNeeded(model, maxTokens)) {
                return@withContext Result.failure(IllegalStateException("No se pudo cargar el modelo: $model"))
            }

            val inference = llmInference ?: return@withContext Result.failure(
                IllegalStateException("Modelo no inicializado")
            )

            // Construir el prompt
            val prompt = buildPrompt(messages, systemPrompt)
            
            // Crear sesión con opciones (sin maxTokens, eso va en LlmInferenceOptions)
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(temperature.coerceIn(0f, 1f))
                .setTopK(40)
                .build()

            // Generar respuesta
            LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                val response = session.generateResponse()
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> = withContext(Dispatchers.Default) {
        try {
            if (!loadModelIfNeeded(model, maxTokens)) {
                return@withContext Result.failure(IllegalStateException("No se pudo cargar el modelo: $model"))
            }

            val inference = llmInference ?: return@withContext Result.failure(
                IllegalStateException("Modelo no inicializado")
            )

            // Construir prompt con herramientas
            val prompt = buildToolPrompt(messages, systemPrompt, tools)
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(temperature.coerceIn(0f, 1f))
                .setTopK(40)
                .build()

            LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                val response = session.generateResponse()
                
                // Parsear si hay llamadas a herramientas
                val toolCalls = parseToolCalls(response)
                
                Result.success(
                    ChatResponseWithTools(
                        content = if (toolCalls.isEmpty()) response else null,
                        toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                        finishReason = "stop"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        val models = modelRepository.getDownloadedModels().map { it.id }
        return Result.success(models)
    }

    private fun loadModelIfNeeded(modelId: String, maxTokens: Int = 4096): Boolean {
        if (currentModel == modelId && llmInference != null) {
            return true
        }

        // Liberar modelo anterior
        unloadModel()

        // Encontrar el modelo
        val model = modelRepository.getAvailableModels().find { it.id == modelId && it.isDownloaded }
            ?: return false

        val modelPath = model.localPath ?: return false

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModel = modelId
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun buildPrompt(messages: List<ChatMessage>, systemPrompt: String): String {
        val sb = StringBuilder()
        
        if (systemPrompt.isNotBlank()) {
            sb.append("<start_of_turn>user\n").append(systemPrompt).append("<end_of_turn>\n")
        }
        
        messages.forEach { msg ->
            when (msg.role) {
                "user" -> sb.append("<start_of_turn>user\n").append(msg.content).append("<end_of_turn>\n")
                "assistant" -> sb.append("<start_of_turn>model\n").append(msg.content).append("<end_of_turn>\n")
            }
        }
        
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun buildToolPrompt(
        messages: List<ChatMessage>,
        systemPrompt: String,
        tools: List<Map<String, Any>>
    ): String {
        val sb = StringBuilder()
        
        sb.appendLine("You are a helpful assistant with access to tools.")
        if (systemPrompt.isNotBlank()) {
            sb.appendLine(systemPrompt)
        }
        
        sb.appendLine("\nAvailable tools:")
        tools.forEach { tool ->
            val name = tool["name"] as? String ?: ""
            val description = tool["description"] as? String ?: ""
            sb.appendLine("- $name: $description")
        }
        
        sb.appendLine("\nIf you need to use a tool, respond with: TOOL_CALL: {\"name\": \"tool_name\", \"arguments\": {...}}")
        sb.appendLine("Otherwise, respond normally.")
        
        messages.forEach { msg ->
            when (msg.role) {
                "user" -> sb.appendLine("User: ${msg.content}")
                "assistant" -> sb.appendLine("Assistant: ${msg.content}")
            }
        }
        
        return sb.toString()
    }

    private fun parseToolCalls(response: String): List<ToolCall> {
        val toolCallRegex = """TOOL_CALL:\s*(\{[^}]+\})""".toRegex()
        val match = toolCallRegex.find(response)
        
        return if (match != null) {
            try {
                val json = org.json.JSONObject(match.groupValues[1])
                val functionName = json.optString("name", "")
                val arguments = json.optJSONObject("arguments")?.toString() ?: "{}"
                
                listOf(
                    ToolCall(
                        id = "call_${System.currentTimeMillis()}",
                        function = ToolFunction(
                            name = functionName,
                            arguments = arguments
                        )
                    )
                )
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun unloadModel() {
        llmInference?.close()
        llmInference = null
        currentModel = null
    }

    suspend fun generateStream(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        try {
            if (!loadModelIfNeeded(model)) {
                onError("No se pudo cargar el modelo: $model")
                return@withContext
            }

            val inference = llmInference ?: run {
                onError("Modelo no inicializado")
                return@withContext
            }

            val prompt = buildPrompt(messages, systemPrompt)
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.7f)
                .setTopK(40)
                .build()

            LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                
                // MediaPipe no tiene streaming directo, simulamos con chunks
                val response = session.generateResponse()
                
                // Simular streaming enviando palabra por palabra
                val words = response.split(" ")
                words.forEach { word ->
                    onDelta("$word ")
                    delay(10) // Pequeña pausa para simular streaming
                }
                onDone()
            }
        } catch (e: Exception) {
            onError(e.message ?: "Error desconocido")
        }
    }
}
