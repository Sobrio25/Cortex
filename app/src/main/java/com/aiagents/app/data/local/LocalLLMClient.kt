package com.aiagents.app.data.local

import android.app.ActivityManager
import android.content.Context
import android.util.Log
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
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import com.google.gson.Gson
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalLLMClient(
    private val context: Context,
    private val modelRepository: LocalModelRepository
) : AIClient {

    private companion object {
        const val TAG = "LocalLLMClient"
        const val IDLE_RELEASE_DELAY_MS = 60_000L
    }

    private val localWebToolNames = setOf("web_search", "web_fetch")
    private val inferenceMutex = Mutex()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleReleaseJob: Job? = null
    private var lastModelLoadError: String? = null

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
        inferenceMutex.withLock {
            cancelIdleRelease()
            try {
                val modelEntity = modelRepository.getAvailableModels().find { it.id == model && it.isDownloaded }
                    ?: return@withLock Result.failure(IllegalStateException("Modelo no encontrado: $model"))

                if (isLitertModel(modelEntity)) {
                    chatWithLiteRT(model, messages, systemPrompt, temperature)
                } else {
                    chatWithMediaPipe(model, messages, systemPrompt, temperature, maxTokens)
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                scheduleIdleRelease()
            }
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
        inferenceMutex.withLock {
            cancelIdleRelease()
            try {
                val modelEntity = modelRepository.getAvailableModels().find { it.id == model && it.isDownloaded }
                    ?: return@withLock Result.failure(IllegalStateException("Modelo no encontrado: $model"))

                // Keep the local surface intentionally small, even for auxiliary callers
                // that may pass the broader cloud tool catalog.
                val localTools = tools.filter { definition ->
                    @Suppress("UNCHECKED_CAST")
                    val function = definition["function"] as? Map<String, Any>
                    function?.get("name") in localWebToolNames
                }

                if (isLitertModel(modelEntity)) {
                    chatWithToolsLiteRT(model, messages, systemPrompt, temperature, localTools)
                } else {
                    chatWithToolsMediaPipe(model, messages, systemPrompt, temperature, maxTokens, localTools)
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                scheduleIdleRelease()
            }
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
            lastModelLoadError = null
            return true
        }

        unloadModelInternal()

        val model = modelRepository.getAvailableModels().find { it.id == modelId && it.isDownloaded }
            ?: return failModelLoad("Modelo no encontrado: $modelId")

        val modelPath = model.localPath
            ?: return failModelLoad("El archivo del modelo no está disponible: $modelId")
        if (!hasSafeMemoryFor(model, modelPath)) return false

        return try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU()
            )
            litertEngine = Engine(engineConfig)
            litertEngine!!.initialize()
            currentLitertModel = modelId
            lastModelLoadError = null
            true
        } catch (e: Exception) {
            unloadLiteRTModel()
            Log.e(TAG, "LiteRT model initialization failed for $modelId", e)
            failModelLoad("No se pudo inicializar el modelo local $modelId: ${e.message ?: "error nativo"}")
        }
    }

    private fun createLiteRTConversation(
        systemPrompt: String,
        temperature: Float,
        toolDefinitions: List<Map<String, Any>> = emptyList()
    ): Conversation? {
        val engine = litertEngine ?: return null

        val toolProviders = toolDefinitions.mapNotNull { definition ->
            @Suppress("UNCHECKED_CAST")
            val function = definition["function"] as? Map<String, Any?> ?: return@mapNotNull null
            val name = function["name"] as? String ?: return@mapNotNull null
            val description = function["description"] as? String ?: ""
            val parameters = function["parameters"] ?: emptyMap<String, Any>()
            val openApiDescription = Gson().toJson(
                mapOf(
                    "name" to name,
                    "description" to description,
                    "parameters" to parameters
                )
            )
            tool(LiteRtLocalTool(openApiDescription))
        }

        val config = ConversationConfig(
            systemInstruction = com.google.ai.edge.litertlm.Contents.of(systemPrompt.takeIf { it.isNotBlank() } ?: "You are a helpful assistant."),
            tools = toolProviders,
            // The host owns execution so the same web-tool dispatcher is used for
            // both MediaPipe and LiteRT local models. LiteRT still exposes the
            // schemas and returns structured tool calls to this class.
            automaticToolCalling = toolProviders.isEmpty(),
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
            return Result.failure(modelLoadException(model))
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
        temperature: Float,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        if (!loadLiteRTModelIfNeeded(model)) {
            return Result.failure(modelLoadException(model))
        }

        val conversation = createLiteRTConversation(systemPrompt, temperature, tools)
            ?: return Result.failure(IllegalStateException("No se pudo crear la conversación"))

        return try {
            val fullPrompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }

            val responseMessages = conversation.sendMessageAsync(fullPrompt)
                .catch { throw it }
                .toList()

            val fullResponse = responseMessages.joinToString("") { it.toString() }
            val toolCalls = responseMessages.flatMapIndexed { messageIndex, msg ->
                msg.toolCalls.mapIndexed { callIndex, toolCall ->
                    ToolCall(
                        id = "litert_${System.currentTimeMillis()}_${messageIndex}_$callIndex",
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
            return Result.failure(modelLoadException(model))
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
            return Result.failure(modelLoadException(model))
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
            lastModelLoadError = null
            return true
        }

        unloadModelInternal()

        val model = modelRepository.getAvailableModels().find { it.id == modelId && it.isDownloaded }
            ?: return failModelLoad("Modelo no encontrado: $modelId")

        val modelPath = model.localPath
            ?: return failModelLoad("El archivo del modelo no está disponible: $modelId")
        if (!hasSafeMemoryFor(model, modelPath)) return false

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModel = modelId
            lastModelLoadError = null
            true
        } catch (e: Exception) {
            try { llmInference?.close() } catch (_: Exception) {}
            llmInference = null
            currentModel = null
            Log.e(TAG, "MediaPipe model initialization failed for $modelId", e)
            failModelLoad("No se pudo inicializar el modelo local $modelId: ${e.message ?: "error nativo"}")
        }
    }

    private fun hasSafeMemoryFor(model: LocalModel, modelPath: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val actualFileBytes = File(modelPath).length().takeIf { it > 0L } ?: model.sizeBytes
        val decision = LocalModelMemoryPolicy.evaluate(
            modelBytes = actualFileBytes,
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem
        )
        if (decision.allowed) return true

        val message = buildString {
            append("El modelo ${model.name} necesita aproximadamente ")
            append(formatGiB(decision.estimatedPeakBytes))
            append(" GB de RAM, pero el límite seguro actual es ")
            append(formatGiB(decision.safeBudgetBytes))
            append(" GB. Usa un modelo local de hasta 1 GB, por ejemplo Gemma 3 1B o FunctionGemma 270M.")
        }
        Log.w(TAG, message)
        return failModelLoad(message)
    }

    private fun failModelLoad(message: String): Boolean {
        lastModelLoadError = message
        return false
    }

    private fun modelLoadException(modelId: String): IllegalStateException =
        IllegalStateException(lastModelLoadError ?: "No se pudo cargar el modelo: $modelId")

    private fun formatGiB(bytes: Long): String =
        String.format(java.util.Locale.US, "%.1f", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

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
        if (systemPrompt.isNotBlank()) {
            sb.appendLine(systemPrompt)
        }
        
        sb.appendLine("\nAvailable tools:")
        tools.forEach { tool ->
            @Suppress("UNCHECKED_CAST")
            val function = tool["function"] as? Map<String, Any>
            val name = function?.get("name") as? String ?: ""
            val description = function?.get("description") as? String ?: ""
            sb.appendLine("- $name: $description")
            function?.get("parameters")?.let { parameters ->
                sb.appendLine("  Parameters: ${Gson().toJson(parameters)}")
            }
        }
        
        sb.appendLine("\nIf you need to use a tool, respond with exactly: TOOL_CALL: {\"name\": \"tool_name\", \"arguments\": { ... }}")
        sb.appendLine("You may emit more than one TOOL_CALL. The name must be web_search or web_fetch and arguments must be a JSON object.")
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
        return LocalToolCallParser.parse(response)
    }

    private fun cancelIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = null
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = lifecycleScope.launch {
            delay(IDLE_RELEASE_DELAY_MS)
            inferenceMutex.withLock {
                unloadModelInternal()
                idleReleaseJob = null
            }
        }
    }

    fun unloadModel() {
        cancelIdleRelease()
        unloadModelInternal()
    }

    private fun unloadModelInternal() {
        try { llmInference?.close() } catch (error: Exception) {
            Log.w(TAG, "MediaPipe model close failed", error)
        }
        llmInference = null
        currentModel = null
        unloadLiteRTModel()
        lastModelLoadError = null
    }

    suspend fun generateStream(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            cancelIdleRelease()
            try {
                val modelEntity = modelRepository.getAvailableModels().find { it.id == model && it.isDownloaded }
                    ?: run { onError("Modelo no encontrado: $model"); return@withLock }

                if (isLitertModel(modelEntity)) {
                    generateStreamLiteRT(model, messages, systemPrompt, onDelta, onDone, onError)
                } else {
                    generateStreamMediaPipe(model, messages, systemPrompt, onDelta, onDone, onError)
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error desconocido")
            } finally {
                scheduleIdleRelease()
            }
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
            onError(modelLoadException(model).message ?: "No se pudo cargar el modelo: $model")
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
            onError(modelLoadException(model).message ?: "No se pudo cargar el modelo: $model")
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

/** Adapter used only to register local web schemas with LiteRT-LM. */
private class LiteRtLocalTool(
    private val descriptionJson: String
) : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = descriptionJson

    override fun execute(paramsJsonString: String): String =
        "{\"error\":\"Tool execution is delegated to the host application\"}"
}
