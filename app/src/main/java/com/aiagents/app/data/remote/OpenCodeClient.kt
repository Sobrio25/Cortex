package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.OpenCodeVariantType
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * OpenCode is a mixed-protocol gateway. The model catalog is OpenAI-compatible, but inference
 * must use the protocol published for each model family.
 */
class OpenCodeClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    baseUrl: String,
    private val variant: OpenCodeVariantType = openCodeVariantFromBaseUrl(baseUrl)
) : AIClient {

    private val effectiveBaseUrl = baseUrl.trimEnd('/') + "/"
    private val gson = Gson()
    private val chatCompletionsClient = OpenAIClient(okHttpClient, apiKey, effectiveBaseUrl)

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = chatWithTools(
        model = model,
        messages = messages,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        tools = emptyList()
    ).map { it.content.orEmpty() }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        val modelId = OpenCodeProtocolResolver.normalizeModelId(model)
        val protocol = OpenCodeProtocolResolver.resolve(variant, modelId)
        Log.d(TAG, "OpenCode ${variant.name}: model=$modelId protocol=$protocol")

        return when (protocol) {
            OpenCodeApiProtocol.CHAT_COMPLETIONS -> chatCompletionsClient.chatWithTools(
                modelId,
                messages,
                systemPrompt,
                temperature,
                maxTokens,
                tools
            )

            OpenCodeApiProtocol.ANTHROPIC_MESSAGES -> requestAnthropicMessages(
                modelId,
                messages,
                systemPrompt,
                temperature,
                maxTokens,
                tools
            )

            OpenCodeApiProtocol.RESPONSES -> requestResponses(
                modelId,
                messages,
                systemPrompt,
                maxTokens,
                tools
            )

            OpenCodeApiProtocol.GEMINI -> requestGemini(
                modelId,
                messages,
                systemPrompt,
                temperature,
                maxTokens,
                tools
            )
        }
    }

    override fun chatWithToolsStreaming(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Flow<StreamingChunk> {
        val modelId = OpenCodeProtocolResolver.normalizeModelId(model)
        return when (OpenCodeProtocolResolver.resolve(variant, modelId)) {
            OpenCodeApiProtocol.CHAT_COMPLETIONS -> chatCompletionsClient.chatWithToolsStreaming(
                modelId,
                messages,
                systemPrompt,
                temperature,
                maxTokens,
                tools
            )

            OpenCodeApiProtocol.ANTHROPIC_MESSAGES -> streamAnthropic(
                okHttpClient = okHttpClient,
                apiKey = apiKey.trim(),
                requestBody = buildAnthropicRequest(
                    modelId,
                    messages,
                    systemPrompt,
                    temperature,
                    maxTokens,
                    tools,
                    stream = true
                ),
                url = "${effectiveBaseUrl}messages",
                extraHeaders = mapOf("anthropic-version" to ANTHROPIC_VERSION)
            )

            // The app can consume a complete response as one streaming chunk. This keeps tool
            // calls reliable for Responses and Gemini while avoiding protocol-specific SSE loss.
            OpenCodeApiProtocol.RESPONSES,
            OpenCodeApiProtocol.GEMINI -> flow {
                chatWithTools(
                    modelId,
                    messages,
                    systemPrompt,
                    temperature,
                    maxTokens,
                    tools
                ).onSuccess { response ->
                    response.reasoning?.takeIf(String::isNotBlank)?.let {
                        emit(StreamingChunk(reasoning = it))
                    }
                    response.content?.takeIf(String::isNotBlank)?.let {
                        emit(StreamingChunk(content = it))
                    }
                    emit(
                        StreamingChunk(
                            done = true,
                            toolCalls = response.toolCalls,
                            finishReason = response.finishReason
                        )
                    )
                }.onFailure { error ->
                    emit(StreamingChunk(error = error.message ?: "Error de OpenCode"))
                }
            }
        }
    }

    override suspend fun getAvailableModels(): Result<List<String>> =
        getAvailableModelInfos().map { models -> models.map { it.id } }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> = runCatching {
        val raw = executeGet(
            url = "${effectiveBaseUrl}models",
            headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}")
        )
        val gatewayModels = OpenCodeCatalogParser.parse(raw)
            .map { it.copy(id = OpenCodeProtocolResolver.normalizeModelId(it.id)) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id.lowercase(Locale.ROOT) }
            .also { check(it.isNotEmpty()) { "OpenCode no devolvió modelos para ${variant.displayName}" } }
        val officialContexts = runCatching { loadOfficialContextWindows() }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "No se pudieron enriquecer los contextos de ${variant.displayName}: ${error.message}"
                )
            }
            .getOrDefault(emptyMap())
        OpenCodeMetadataMerger.enrich(gatewayModels, officialContexts).also { enriched ->
            Log.d(
                TAG,
                "${variant.displayName}: ${enriched.count { it.contextWindow != null }}/${enriched.size} " +
                    "modelos con límite de contexto"
            )
        }
    }.onFailure { error ->
        Log.e(TAG, "No se pudo cargar el catálogo de ${variant.displayName}: ${error.message}")
    }

    private suspend fun loadOfficialContextWindows(): Map<String, Int> {
        val now = System.currentTimeMillis()
        modelsDevContextCache
            ?.takeIf { now - it.loadedAtEpochMillis < MODELS_DEV_CACHE_TTL_MILLIS }
            ?.let { return it.byVariant[variant].orEmpty() }

        return modelsDevContextMutex.withLock {
            val refreshedAt = System.currentTimeMillis()
            modelsDevContextCache
                ?.takeIf { refreshedAt - it.loadedAtEpochMillis < MODELS_DEV_CACHE_TTL_MILLIS }
                ?.let { return@withLock it.byVariant[variant].orEmpty() }

            val parsed = ModelsDevOpenCodeMetadataParser.parse(executePublicGet(MODELS_DEV_API_URL))
            modelsDevContextCache = ModelsDevContextCache(refreshedAt, parsed)
            parsed[variant].orEmpty()
        }
    }

    private suspend fun requestAnthropicMessages(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> = runCatching {
        val response = executePost(
            url = "${effectiveBaseUrl}messages",
            headers = mapOf(
                "x-api-key" to apiKey.trim(),
                "anthropic-version" to ANTHROPIC_VERSION
            ),
            body = buildAnthropicRequest(
                model,
                messages,
                systemPrompt,
                temperature,
                maxTokens,
                tools,
                stream = false
            )
        )
        parseAnthropicResponse(response)
    }

    private fun buildAnthropicRequest(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>,
        stream: Boolean
    ): Map<String, Any?> = buildMap {
        put("model", model)
        put("messages", buildAnthropicMessages(messages))
        put("max_tokens", maxTokens)
        put("temperature", temperature)
        put("stream", stream)
        systemPrompt.takeIf(String::isNotBlank)?.let { put("system", it) }
        if (tools.isNotEmpty()) put("tools", tools.map(::toAnthropicTool))
    }

    private fun buildAnthropicMessages(messages: List<ChatMessage>): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            if (message.role == "tool") {
                val blocks = mutableListOf<Map<String, Any>>()
                while (index < messages.size && messages[index].role == "tool") {
                    val toolMessage = messages[index]
                    val resultContent = mutableListOf<Map<String, Any>>()
                    toolMessage.toolResultImageUri?.let { uri ->
                        dataUriParts(uri)?.let { (mediaType, data) ->
                            resultContent += mapOf(
                                "type" to "image",
                                "source" to mapOf(
                                    "type" to "base64",
                                    "media_type" to mediaType,
                                    "data" to data
                                )
                            )
                        }
                    }
                    if (toolMessage.content.isNotBlank()) {
                        resultContent += mapOf("type" to "text", "text" to toolMessage.content)
                    }
                    blocks += buildMap {
                        put("type", "tool_result")
                        put("tool_use_id", toolMessage.toolCallId.orEmpty())
                        put("content", resultContent.ifEmpty {
                            listOf(mapOf("type" to "text", "text" to ""))
                        })
                    }
                    index++
                }
                result += mapOf("role" to "user", "content" to blocks)
                continue
            }

            val blocks = mutableListOf<Map<String, Any>>()
            if (message.content.isNotBlank()) {
                blocks += mapOf("type" to "text", "text" to message.content)
            }
            message.toolCalls.orEmpty().forEach { call ->
                blocks += mapOf(
                    "type" to "tool_use",
                    "id" to call.id,
                    "name" to call.function.name,
                    "input" to parseArguments(call.function.arguments)
                )
            }
            message.imageDataUri?.let { uri ->
                dataUriParts(uri)?.let { (mediaType, data) ->
                    blocks += mapOf(
                        "type" to "image",
                        "source" to mapOf(
                            "type" to "base64",
                            "media_type" to mediaType,
                            "data" to data
                        )
                    )
                }
            }
            result += mapOf(
                "role" to if (message.role == "assistant") "assistant" else "user",
                "content" to blocks.ifEmpty { listOf(mapOf("type" to "text", "text" to "")) }
            )
            index++
        }
        return result
    }

    private fun toAnthropicTool(tool: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val function = tool["function"] as? Map<String, Any> ?: return tool
        return mapOf(
            "name" to function["name"].orEmptyString(),
            "description" to function["description"].orEmptyString(),
            "input_schema" to (function["parameters"] ?: mapOf("type" to "object"))
        )
    }

    private fun parseAnthropicResponse(root: JsonObject): ChatResponseWithTools {
        root.apiError()?.let { error(it) }
        val content = root.array("content")
        val text = content.mapNotNull { block ->
            block.asObjectOrNull()
                ?.takeIf { it.string("type") == "text" }
                ?.string("text")
        }.joinToString("").ifBlank { null }
        val reasoning = content.mapNotNull { block ->
            block.asObjectOrNull()
                ?.takeIf { it.string("type") == "thinking" }
                ?.let { it.string("thinking") ?: it.string("text") }
        }.joinToString("\n").ifBlank { null }
        val toolCalls = content.mapNotNull { block ->
            val item = block.asObjectOrNull()?.takeIf { it.string("type") == "tool_use" }
                ?: return@mapNotNull null
            ToolCall(
                id = item.string("id").orEmpty(),
                type = "function",
                function = ToolFunction(
                    name = item.string("name").orEmpty(),
                    arguments = item.get("input")?.let(gson::toJson) ?: "{}"
                )
            )
        }.ifEmpty { null }
        return ChatResponseWithTools(text, toolCalls, root.string("stop_reason"), reasoning)
    }

    private suspend fun requestResponses(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> = runCatching {
        val body = buildMap<String, Any?> {
            put("model", model)
            put("input", buildResponsesInput(messages))
            put("max_output_tokens", maxTokens)
            systemPrompt.takeIf(String::isNotBlank)?.let { put("instructions", it) }
            if (tools.isNotEmpty()) put("tools", tools.map(::toResponsesTool))
        }
        parseResponsesResponse(
            executePost(
                url = "${effectiveBaseUrl}responses",
                headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}"),
                body = body
            )
        )
    }

    private fun buildResponsesInput(messages: List<ChatMessage>): List<Map<String, Any>> =
        buildList {
            messages.forEach { message ->
                if (message.role == "tool") {
                    add(
                        mapOf(
                            "type" to "function_call_output",
                            "call_id" to message.toolCallId.orEmpty(),
                            "output" to message.content
                        )
                    )
                    return@forEach
                }

                if (message.content.isNotBlank() || message.imageDataUri != null) {
                    val content: Any = if (message.imageDataUri == null) {
                        message.content
                    } else {
                        buildList<Map<String, Any>> {
                            if (message.content.isNotBlank()) {
                                add(mapOf("type" to "input_text", "text" to message.content))
                            }
                            add(mapOf("type" to "input_image", "image_url" to message.imageDataUri))
                        }
                    }
                    add(
                        mapOf(
                            "role" to if (message.role == "assistant") "assistant" else "user",
                            "content" to content
                        )
                    )
                }
                message.toolCalls.orEmpty().forEach { call ->
                    add(
                        mapOf(
                            "type" to "function_call",
                            "call_id" to call.id,
                            "name" to call.function.name,
                            "arguments" to call.function.arguments
                        )
                    )
                }
            }
        }

    private fun toResponsesTool(tool: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val function = tool["function"] as? Map<String, Any> ?: return tool
        return buildMap {
            put("type", "function")
            put("name", function["name"].orEmptyString())
            function["description"]?.let { put("description", it) }
            put("parameters", function["parameters"] ?: mapOf("type" to "object"))
        }
    }

    private fun parseResponsesResponse(root: JsonObject): ChatResponseWithTools {
        root.apiError()?.let { error(it) }
        val output = root.array("output")
        val textParts = mutableListOf<String>()
        val reasoningParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()
        output.forEach { element ->
            val item = element.asObjectOrNull() ?: return@forEach
            when (item.string("type")) {
                "message" -> item.array("content").forEach contentLoop@{ contentElement ->
                    val content = contentElement.asObjectOrNull() ?: return@contentLoop
                    when (content.string("type")) {
                        "output_text", "text" -> content.string("text")?.let(textParts::add)
                        "reasoning_text", "summary_text" -> content.string("text")?.let(reasoningParts::add)
                    }
                }
                "function_call" -> toolCalls += ToolCall(
                    id = item.string("call_id") ?: item.string("id").orEmpty(),
                    type = "function",
                    function = ToolFunction(
                        name = item.string("name").orEmpty(),
                        arguments = item.string("arguments") ?: "{}"
                    )
                )
                "reasoning" -> item.array("summary").forEach summaryLoop@{ summaryElement ->
                    val summary = summaryElement.asObjectOrNull() ?: return@summaryLoop
                    summary.string("text")?.let(reasoningParts::add)
                }
            }
        }
        if (textParts.isEmpty()) root.string("output_text")?.let(textParts::add)
        return ChatResponseWithTools(
            content = textParts.joinToString("").ifBlank { null },
            toolCalls = toolCalls.ifEmpty { null },
            finishReason = root.string("status"),
            reasoning = reasoningParts.joinToString("\n").ifBlank { null }
        )
    }

    private suspend fun requestGemini(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> = runCatching {
        val encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8.toString())
        val body = buildMap<String, Any> {
            put("contents", buildGeminiContents(messages))
            put(
                "generationConfig",
                mapOf("temperature" to temperature, "maxOutputTokens" to maxTokens)
            )
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", mapOf("parts" to listOf(mapOf("text" to systemPrompt))))
            }
            if (tools.isNotEmpty()) {
                put("tools", listOf(mapOf("functionDeclarations" to tools.map(::toGeminiTool))))
            }
        }
        parseGeminiResponse(
            executePost(
                url = "${effectiveBaseUrl}models/$encodedModel:generateContent",
                headers = mapOf("x-goog-api-key" to apiKey.trim()),
                body = body
            )
        )
    }

    private fun buildGeminiContents(messages: List<ChatMessage>): List<Map<String, Any>> =
        messages.map { message ->
            val parts = mutableListOf<Map<String, Any>>()
            if (message.role == "tool") {
                parts += buildMap {
                    put(
                        "functionResponse",
                        buildMap<String, Any> {
                            put("name", message.name.orEmpty())
                            put("response", mapOf("result" to message.content))
                            message.toolCallId?.let { put("id", it) }
                        }
                    )
                }
            } else {
                if (message.content.isNotBlank()) parts += mapOf("text" to message.content)
                message.imageDataUri?.let { uri ->
                    dataUriParts(uri)?.let { (mediaType, data) ->
                        parts += mapOf("inlineData" to mapOf("mimeType" to mediaType, "data" to data))
                    }
                }
                message.toolCalls.orEmpty().forEach { call ->
                    parts += mapOf(
                        "functionCall" to mapOf(
                            "id" to call.id,
                            "name" to call.function.name,
                            "args" to parseArguments(call.function.arguments)
                        )
                    )
                }
            }
            mapOf(
                "role" to if (message.role == "assistant") "model" else "user",
                "parts" to parts.ifEmpty { listOf(mapOf("text" to "")) }
            )
        }

    private fun toGeminiTool(tool: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val function = tool["function"] as? Map<String, Any> ?: return tool
        return buildMap {
            put("name", function["name"].orEmptyString())
            function["description"]?.let { put("description", it) }
            put("parameters", function["parameters"] ?: mapOf("type" to "object"))
        }
    }

    private fun parseGeminiResponse(root: JsonObject): ChatResponseWithTools {
        root.apiError()?.let { error(it) }
        val candidate = root.array("candidates").firstOrNull()?.asObjectOrNull()
            ?: error("OpenCode Gemini no devolvió candidatos")
        val parts = candidate.objectValue("content")?.array("parts").orEmpty()
        val text = parts.mapNotNull { it.asObjectOrNull()?.string("text") }
            .joinToString("").ifBlank { null }
        val toolCalls = parts.mapIndexedNotNull { index, element ->
            val call = element.asObjectOrNull()?.objectValue("functionCall")
                ?: return@mapIndexedNotNull null
            ToolCall(
                id = call.string("id") ?: "gemini_call_$index",
                type = "function",
                function = ToolFunction(
                    name = call.string("name").orEmpty(),
                    arguments = call.get("args")?.let(gson::toJson) ?: "{}"
                )
            )
        }.ifEmpty { null }
        return ChatResponseWithTools(
            content = text,
            toolCalls = toolCalls,
            finishReason = candidate.string("finishReason"),
            reasoning = null
        )
    }

    private suspend fun executeGet(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get().addHeader("Accept", "application/json")
            headers.filterValues(String::isNotBlank).forEach(builder::addHeader)
            okHttpClient.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw requestError(response.code, raw)
                raw
            }
        }

    private suspend fun executePublicGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { "models.dev respondió HTTP ${response.code}" }
            raw
        }
    }

    private suspend fun executePost(
        url: String,
        headers: Map<String, String>,
        body: Map<String, Any?>
    ): JsonObject = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(body.filterValues { it != null })
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
        headers.filterValues(String::isNotBlank).forEach(builder::addHeader)
        okHttpClient.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw requestError(response.code, raw)
            JsonParser.parseString(raw).asObjectOrNull()
                ?: error("OpenCode devolvió una respuesta JSON inválida")
        }
    }

    private fun requestError(statusCode: Int, rawBody: String): OpenCodeRequestException {
        val detail = extractApiError(rawBody)
        val message = when (statusCode) {
            401 -> "La API key no es válida para ${variant.displayName}"
            403 -> "La API key no tiene acceso a ${variant.displayName} o al modelo seleccionado"
            404 -> detail ?: "El modelo o endpoint ya no está disponible en ${variant.displayName}"
            429 -> detail ?: "OpenCode alcanzó el límite de uso; inténtalo de nuevo más tarde"
            else -> detail ?: "OpenCode respondió HTTP $statusCode"
        }
        Log.e(TAG, "${variant.displayName} HTTP $statusCode: $message")
        return OpenCodeRequestException(statusCode, message)
    }

    private fun extractApiError(rawBody: String): String? = runCatching {
        val root = JsonParser.parseString(rawBody).asObjectOrNull() ?: return@runCatching null
        root.apiError() ?: root.string("message")
    }.getOrNull()?.replace(Regex("\\s+"), " ")?.trim()?.take(240)

    private fun parseArguments(arguments: String): Map<String, Any> = runCatching {
        @Suppress("UNCHECKED_CAST")
        gson.fromJson(arguments, Map::class.java) as? Map<String, Any>
    }.getOrNull().orEmpty()

    private fun dataUriParts(uri: String): Pair<String, String>? {
        if (!uri.startsWith("data:")) return null
        val separator = uri.indexOf(";base64,")
        if (separator < 5) return null
        return uri.substring(5, separator) to uri.substring(separator + 8)
    }

    private companion object {
        const val TAG = "OpenCodeClient"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODELS_DEV_API_URL = "https://models.dev/api.json"
        const val MODELS_DEV_CACHE_TTL_MILLIS = 6 * 60 * 60 * 1_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val modelsDevContextMutex = Mutex()

        @Volatile
        var modelsDevContextCache: ModelsDevContextCache? = null
    }
}

private data class ModelsDevContextCache(
    val loadedAtEpochMillis: Long,
    val byVariant: Map<OpenCodeVariantType, Map<String, Int>>
)

internal enum class OpenCodeApiProtocol {
    CHAT_COMPLETIONS,
    ANTHROPIC_MESSAGES,
    RESPONSES,
    GEMINI
}

internal object OpenCodeProtocolResolver {
    fun normalizeModelId(model: String): String = model.trim()
        .removePrefix("opencode-go/")
        .removePrefix("opencode/")

    fun resolve(variant: OpenCodeVariantType, model: String): OpenCodeApiProtocol {
        val id = normalizeModelId(model).lowercase(Locale.ROOT)
        return when {
            id.startsWith("gemini-") -> OpenCodeApiProtocol.GEMINI
            id.startsWith("gpt-") -> OpenCodeApiProtocol.RESPONSES
            id.startsWith("claude-") || id.startsWith("qwen") -> {
                OpenCodeApiProtocol.ANTHROPIC_MESSAGES
            }
            variant == OpenCodeVariantType.GO && id.startsWith("minimax-") -> {
                OpenCodeApiProtocol.ANTHROPIC_MESSAGES
            }
            else -> OpenCodeApiProtocol.CHAT_COMPLETIONS
        }
    }
}

internal object OpenCodeCatalogParser {
    fun parse(rawJson: String): List<RemoteModelInfo> {
        val root = JsonParser.parseString(rawJson)
        val entries = when {
            root.isJsonArray -> root.asJsonArray.mapNotNull { it.asObjectOrNull()?.let { item -> null to item } }
            root.isJsonObject -> parseObject(root.asJsonObject)
            else -> emptyList()
        }
        return entries.mapNotNull { (fallbackId, item) ->
            val id = item.string("id") ?: fallbackId ?: return@mapNotNull null
            RemoteModelInfo(id = id, contextWindow = contextWindow(item))
        }
    }

    private fun parseObject(root: JsonObject): List<Pair<String?, JsonObject>> {
        listOf("data", "models").forEach { key ->
            val element = root.get(key) ?: return@forEach
            if (element.isJsonArray) {
                return element.asJsonArray.mapNotNull { it.asObjectOrNull()?.let { item -> null to item } }
            }
            if (element.isJsonObject) {
                return element.asJsonObject.entrySet().mapNotNull { (id, value) ->
                    value.asObjectOrNull()?.let { id to it }
                }
            }
        }
        return root.entrySet().mapNotNull { (id, value) ->
            value.asObjectOrNull()?.takeIf { it.has("id") || it.has("limit") }?.let { id to it }
        }
    }

    private fun contextWindow(item: JsonObject): Int? {
        val direct = listOf("context_length", "context_window", "contextWindow")
            .firstNotNullOfOrNull { key -> item.positiveInt(key) }
        if (direct != null) return direct
        return item.objectValue("limit")?.positiveInt("context")
    }
}

internal object ModelsDevOpenCodeMetadataParser {
    fun parse(rawJson: String): Map<OpenCodeVariantType, Map<String, Int>> {
        val root = JsonParser.parseString(rawJson).asObjectOrNull() ?: return emptyMap()
        return OpenCodeVariantType.entries.associateWith { variant ->
            val providerId = when (variant) {
                OpenCodeVariantType.ZEN -> "opencode"
                OpenCodeVariantType.GO -> "opencode-go"
            }
            root.objectValue(providerId)
                ?.objectValue("models")
                ?.entrySet()
                ?.mapNotNull { (modelId, value) ->
                    val context = value.asObjectOrNull()
                        ?.objectValue("limit")
                        ?.positiveInt("context")
                        ?: return@mapNotNull null
                    OpenCodeProtocolResolver.normalizeModelId(modelId)
                        .lowercase(Locale.ROOT) to context
                }
                ?.toMap()
                .orEmpty()
        }
    }
}

internal object OpenCodeMetadataMerger {
    fun enrich(
        models: List<RemoteModelInfo>,
        officialContextWindows: Map<String, Int>
    ): List<RemoteModelInfo> = models.map { model ->
        if (model.contextWindow != null) return@map model
        val normalizedId = OpenCodeProtocolResolver.normalizeModelId(model.id).lowercase(Locale.ROOT)
        model.copy(contextWindow = officialContextWindows[normalizedId])
    }
}

internal class OpenCodeRequestException(
    val statusCode: Int,
    message: String
) : Exception(message)

private fun openCodeVariantFromBaseUrl(baseUrl: String): OpenCodeVariantType =
    if (baseUrl.trimEnd('/').endsWith("/go/v1")) OpenCodeVariantType.GO else OpenCodeVariantType.ZEN

private fun JsonElement.asObjectOrNull(): JsonObject? =
    if (isJsonObject) asJsonObject else null

private fun JsonObject.string(key: String): String? = get(key)?.let { value ->
    if (value.isJsonNull || !value.isJsonPrimitive) null else runCatching { value.asString }.getOrNull()
}

private fun JsonObject.array(key: String): List<JsonElement> = get(key)?.let { value ->
    if (value.isJsonArray) value.asJsonArray.toList() else emptyList()
}.orEmpty()

private fun JsonObject.objectValue(key: String): JsonObject? = get(key)?.asObjectOrNull()

private fun JsonObject.positiveInt(key: String): Int? = get(key)?.let { value ->
    if (!value.isJsonPrimitive) return@let null
    runCatching { value.asInt }.getOrNull()?.takeIf { it > 0 }
}

private fun JsonObject.apiError(): String? {
    val error = get("error") ?: return null
    if (error.isJsonPrimitive) return runCatching { error.asString }.getOrNull()
    val objectError = error.asObjectOrNull() ?: return null
    return objectError.string("message") ?: objectError.string("type")
}

private fun Any?.orEmptyString(): String = this?.toString().orEmpty()
