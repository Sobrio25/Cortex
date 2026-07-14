package com.aiagents.app.data.local

import android.content.Context
import com.aiagents.app.data.remote.AIClient
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.remote.ChatResponseWithTools
import com.aiagents.app.domain.model.LocalModel
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

class LocalLLMClient(
    private val context: Context,
    private val modelRepository: LocalModelRepository
) : AIClient {

    // MediaPipe (legacy .task/.bin)
    private var llmInference: LlmInference? = null
    private var currentModel: String? = null

    // LiteRT-LM (.litertlm)
    private var litertEngine: Engine? = null
    private var litertConversation: Conversation? = null
    private var currentLitertModel: String? = null

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            val modelEntity = modelRepository.getAvailableModels().find { it.id == model && it.isDownloaded }
                ?: return@withContext Result.failure(IllegalStateException("Modelo no encontrado: $model"))

            if (isLitertModel(modelEntity)) {
                chatWithLiteRT(model, messages, systemPrompt, temperature)
            } else {
                chatWithMediaPipe(model, messages, systemPrompt, temperature, maxTokens)
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
            val modelEntity = modelRepository.getAvailableModels().find { it.id == model && it.isDownloaded }
                ?: return@withContext Result.failure(IllegalStateException("Modelo no encontrado: $model"))

            if (isLitertModel(modelEntity)) {
                chatWithToolsLiteRT(model, messages, systemPrompt, temperature)
            } else {
                chatWithToolsMediaPipe(model, messages, systemPrompt, temperature, maxTokens, tools)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        val models = modelRepository.getDownloadedModels().map { it.id }
        return Result.success(models)
    }

    // ─── LiteRT-LM (.litertlm) ──────────────────────────────────────────────

    private fun isLitertModel(model: LocalModel): Boolean {
        return model.fileName.endsWith(".litertlm")
    }

    private fun loadLiteRTModelIfNeeded(modelId: String): Boolean {
        if (currentLitertModel == modelId && litertEngine != null) {
            return true
        }

        unloadLiteRTModel()

        val model = modelRepository.getAvailableModels().find { it.id == modelId && it.isDownloaded }
            ?: return false

        val modelPath = model.localPath ?: return false

        return try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU()
            )
            litertEngine = Engine(engineConfig)
            litertEngine!!.initialize()
            currentLitertModel = modelId
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createLiteRTConversation(systemPrompt: String, temperature: Float): Conversation? {
        val engine = litertEngine ?: return null

        val config = ConversationConfig(
            systemInstruction = com.google.ai.edge.litertlm.Contents.of(systemPrompt.takeIf { it.isNotBlank() } ?: "You are a helpful assistant."),
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.95,
                temperature = temperature.coerceIn(0f, 1f).toDouble()
            )
        )
        return engine.createConversation(config)
    }

    private suspend fun chatWithLiteRT(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float
    ): Result<String> {
        if (!loadLiteRTModelIfNeeded(model)) {
            return Result.failure(IllegalStateException("No se pudo cargar el modelo: $model"))
        }

        val conversation = createLiteRTConversation(systemPrompt, temperature)
            ?: return Result.failure(IllegalStateException("No se pudo crear la conversación"))

        return try {
            val userMessage = messages.filter { it.role == "user" }.joinToString("\n") { it.content }
            val fullPrompt = if (messages.size > 1) {
                messages.joinToString("\n") { "${it.role}: ${it.content}" }
            } else {
                userMessage
            }

            val response = conversation.sendMessageAsync(fullPrompt)
                .catch { throw it }
                .toList()
                .joinToString("") { it.toString() }

            conversation.close()
            Result.success(response)
        } catch (e: Exception) {
            try { conversation.close() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private suspend fun chatWithToolsLiteRT(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float
    ): Result<ChatResponseWithTools> {
        if (!loadLiteRTModelIfNeeded(model)) {
            return Result.failure(IllegalStateException("No se pudo cargar el modelo: $model"))
        }

        val conversation = createLiteRTConversation(systemPrompt, temperature)
            ?: return Result.failure(IllegalStateException("No se pudo crear la conversación"))

        return try {
            val fullPrompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }

            val responseMessages = conversation.sendMessageAsync(fullPrompt)
                .catch { throw it }
                .toList()

            val fullResponse = responseMessages.joinToString("") { it.toString() }
            val toolCalls = responseMessages.flatMap { msg ->
                msg.toolCalls.map { toolCall ->
                    ToolCall(
                        id = "litert_${System.currentTimeMillis()}",
                        function = ToolFunction(
                            name = toolCall.name,
                            arguments = com.google.gson.Gson().toJson(toolCall.arguments)
                        )
                    )
                }
            }

            conversation.close()
            Result.success(
                ChatResponseWithTools(
                    content = if (toolCalls.isEmpty()) fullResponse else null,
                    toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                    finishReason = "stop"
                )
            )
        } catch (e: Exception) {
            try { conversation.close() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private fun unloadLiteRTModel() {
        try { litertConversation?.close() } catch (_: Exception) {}
        try { litertEngine?.close() } catch (_: Exception) {}
        litertEngine = null
        litertConversation = null
        currentLitertModel = null
    }

    // ─── MediaPipe (.task/.bin) ─────────────────────────────────────────────

    private suspend fun chatWithMediaPipe(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        if (!loadModelIfNeeded(model, maxTokens)) {
            return Result.failure(IllegalStateException("No se pudo cargar el modelo: $model"))
        }

        val inference = llmInference ?: return Result.failure(
            IllegalStateException("Modelo no inicializado")
        )

        val prompt = buildPrompt(messages, systemPrompt)

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(temperature.coerceIn(0f, 1f))
            .setTopK(40)
            .build()

        return try {
            LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                val response = session.generateResponse()
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun chatWithToolsMediaPipe(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        if (!loadModelIfNeeded(model, maxTokens)) {
            return Result.failure(IllegalStateException("No se pudo cargar el modelo: $model"))
        }

        val inference = llmInference ?: return Result.failure(
            IllegalStateException("Modelo no inicializado")
        )

        val prompt = buildToolPrompt(messages, systemPrompt, tools)

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(temperature.coerceIn(0f, 1f))
            .setTopK(40)
            .build()

        return try {
            LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                val response = session.generateResponse()
                
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

    private fun loadModelIfNeeded(modelId: String, maxTokens: Int = 4096): Boolean {
        if (currentModel == modelId && llmInference != null) {
            return true
        }

        unloadModel()

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
        unloadLiteRTModel()
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
            val modelEntity = modelRepository.getAvailableModels().find { it.id == model && it.isDownloaded }
                ?: run { onError("Modelo no encontrado: $model"); return@withContext }

            if (isLitertModel(modelEntity)) {
                generateStreamLiteRT(model, messages, systemPrompt, onDelta, onDone, onError)
            } else {
                generateStreamMediaPipe(model, messages, systemPrompt, onDelta, onDone, onError)
            }
        } catch (e: Exception) {
            onError(e.message ?: "Error desconocido")
        }
    }

    private suspend fun generateStreamLiteRT(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!loadLiteRTModelIfNeeded(model)) {
            onError("No se pudo cargar el modelo: $model")
            return
        }

        val conversation = createLiteRTConversation(systemPrompt, 0.7f)
            ?: run { onError("No se pudo crear la conversación"); return }

        try {
            val fullPrompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }

            conversation.sendMessageAsync(fullPrompt)
                .catch { throw it }
                .collect { message ->
                    onDelta(message.toString())
                }
            
            conversation.close()
            onDone()
        } catch (e: Exception) {
            try { conversation.close() } catch (_: Exception) {}
            onError(e.message ?: "Error desconocido")
        }
    }

    private suspend fun generateStreamMediaPipe(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!loadModelIfNeeded(model)) {
            onError("No se pudo cargar el modelo: $model")
            return
        }

        val inference = llmInference ?: run {
            onError("Modelo no inicializado")
            return
        }

        val prompt = buildPrompt(messages, systemPrompt)

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(0.7f)
            .setTopK(40)
            .build()

        try {
            LlmInferenceSession.createFromOptions(inference, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                
                val response = session.generateResponse()
                
                val words = response.split(" ")
                words.forEach { word ->
                    onDelta("$word ")
                    delay(10)
                }
                onDone()
            }
        } catch (e: Exception) {
            onError(e.message ?: "Error desconocido")
        }
    }
}
