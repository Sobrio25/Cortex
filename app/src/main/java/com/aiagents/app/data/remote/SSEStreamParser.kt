package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader

private const val TAG = "SSEStreamParser"
private val gson = Gson()

/** Serialize a Map to JSON, filtering out null values at the top level */
private fun toJsonNoNulls(map: Map<String, Any?>): String {
    val filtered = map.filterValues { it != null }
    return gson.toJson(filtered)
}

/**
 * Converts a ChatMessage to a Map for streaming requests, omitting null fields.
 * This matches the behavior of Retrofit+Gson which skips null fields by default.
 */
fun ChatMessage.toStreamingMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>("role" to role)
    // content: use vision format if imageDataUri present
    if (imageDataUri != null) {
        map["content"] = listOf(
            mapOf("type" to "text", "text" to content.ifBlank { "Imagen:" }),
            mapOf("type" to "image_url", "image_url" to mapOf("url" to imageDataUri))
        )
    } else {
        map["content"] = content
    }
    toolCalls?.let { tcs ->
        // Normalize type to "function" for OpenAI-compatible APIs (may be "tool_use" from Anthropic providers)
        map["tool_calls"] = tcs.map { tc ->
            if (tc.type != "function") tc.copy(type = "function") else tc
        }
    }
    toolCallId?.let { map["tool_call_id"] = it }
    name?.let { map["name"] = it }
    return map
}

/**
 * Streams an OpenAI-compatible SSE chat completion and emits StreamingChunks.
 * Works with OpenAI, OpenRouter, DeepSeek, Grok, Ollama, and any OpenAI-compatible API.
 */
fun streamOpenAICompatible(
    okHttpClient: OkHttpClient,
    url: String,
    headers: Map<String, String>,
    requestBody: Map<String, Any?>
): Flow<StreamingChunk> = flow {
    val body = toJsonNoNulls(requestBody)
        .toRequestBody("application/json".toMediaType())

    val requestBuilder = Request.Builder().url(url).post(body)
    headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
    val request = requestBuilder.build()

    val response = okHttpClient.newCall(request).execute()

    if (!response.isSuccessful) {
        val errorBody = response.body?.string() ?: "HTTP ${response.code}"
        emit(StreamingChunk(error = "Error ${response.code}: $errorBody"))
        response.close()
        return@flow
    }

    val reader = response.body?.byteStream()?.bufferedReader()
    if (reader == null) {
        emit(StreamingChunk(error = "Empty response body"))
        response.close()
        return@flow
    }

    val contentBuilder = StringBuilder()
    val reasoningBuilder = StringBuilder()
    val toolCallsMap = mutableMapOf<Int, MutableToolCallAccumulator>()
    var finishReason: String? = null

    try {
        reader.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue

                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    val choices = json.getAsJsonArray("choices") ?: continue
                    if (choices.size() == 0) continue

                    val choice = choices[0].asJsonObject
                    finishReason = choice.get("finish_reason")?.let {
                        if (it.isJsonNull) null else it.asString
                    }
                    val delta = choice.getAsJsonObject("delta") ?: continue

                    // Content delta
                    val contentDelta = delta.get("content")?.let {
                        if (it.isJsonNull) null else it.asString
                    }
                    if (!contentDelta.isNullOrEmpty()) {
                        contentBuilder.append(contentDelta)
                        emit(StreamingChunk(content = contentDelta))
                    }

                    // Reasoning delta (OpenAI o1, DeepSeek R1, etc.)
                    val reasoningDelta = delta.get("reasoning")?.let {
                        if (it.isJsonNull) null else it.asString
                    } ?: delta.get("reasoning_content")?.let {
                        if (it.isJsonNull) null else it.asString
                    } ?: delta.get("thinking")?.let {
                        if (it.isJsonNull) null else it.asString
                    }
                    if (!reasoningDelta.isNullOrEmpty()) {
                        reasoningBuilder.append(reasoningDelta)
                        emit(StreamingChunk(reasoning = reasoningDelta))
                    }

                    // Tool call deltas
                    val toolCallDeltas = delta.getAsJsonArray("tool_calls")
                    if (toolCallDeltas != null) {
                        for (tc in toolCallDeltas) {
                            val tcObj = tc.asJsonObject
                            val index = tcObj.get("index")?.asInt ?: 0
                            val acc = toolCallsMap.getOrPut(index) {
                                MutableToolCallAccumulator()
                            }
                            tcObj.get("id")?.asString?.let { acc.id = it }
                            tcObj.getAsJsonObject("function")?.let { fn ->
                                fn.get("name")?.asString?.let { acc.functionName = it }
                                fn.get("arguments")?.asString?.let { acc.argumentsBuilder.append(it) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing SSE chunk: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading SSE stream", e)
        emit(StreamingChunk(error = "Stream error: ${e.message}"))
        return@flow
    } finally {
        response.close()
    }

    // Note: <think> tag extraction is now handled in real-time by
    // withRealtimeThinkTagParsing() — no need for post-stream extraction here.

    // Build final tool calls from standard OpenAI delta format
    var toolCalls = if (toolCallsMap.isNotEmpty()) {
        toolCallsMap.entries.sortedBy { it.key }.map { (_, acc) ->
            ToolCall(
                id = acc.id ?: "call_${System.currentTimeMillis()}",
                type = "function",
                function = ToolFunction(
                    name = acc.functionName ?: "",
                    arguments = acc.argumentsBuilder.toString()
                )
            )
        }
    } else null

    // Fallback: parse XML-style tool calls from content (MiniMax, some OpenRouter models)
    // Format: <minimax:tool_call> <invoke name="tool_name"> <parameter name="k">v</parameter> ... </invoke> </minimax:tool_call>
    // Also handles generic: <tool_call> <invoke name="..."> ... </invoke> </tool_call>
    val fullContent = contentBuilder.toString()
    if (toolCalls == null && fullContent.contains("<invoke name=")) {
        val parsed = parseXmlToolCalls(fullContent)
        if (parsed.isNotEmpty()) {
            toolCalls = parsed
            // Strip tool call XML from content so the user doesn't see raw XML
            val cleanedContent = fullContent
                .replace(Regex("<(?:minimax:)?tool_call>.*?</(?:minimax:)?tool_call>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<invoke name=.*?</invoke>", RegexOption.DOT_MATCHES_ALL), "")
                .trim()
            contentBuilder.clear()
            contentBuilder.append(cleanedContent)
        }
    }

    emit(StreamingChunk(
        done = true,
        toolCalls = toolCalls,
        finishReason = finishReason,
        content = if (toolCalls != null && contentBuilder.isNotEmpty()) null else null // content already streamed
    ))
}.flowOn(Dispatchers.IO)

/**
 * Streams an Anthropic-format SSE messages API and emits StreamingChunks.
 * Used by AnthropicClient and Kimi Coding (same event format, different URL).
 */
fun streamAnthropic(
    okHttpClient: OkHttpClient,
    apiKey: String,
    requestBody: Map<String, Any?>,
    url: String = "https://api.anthropic.com/v1/messages",
    extraHeaders: Map<String, String> = mapOf("anthropic-version" to "2023-06-01")
): Flow<StreamingChunk> = flow {
    val body = toJsonNoNulls(requestBody)
        .toRequestBody("application/json".toMediaType())

    val requestBuilder = Request.Builder()
        .url(url)
        .addHeader("x-api-key", apiKey)
        .addHeader("Content-Type", "application/json")
        .post(body)
    extraHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
    val request = requestBuilder.build()

    val response = okHttpClient.newCall(request).execute()

    if (!response.isSuccessful) {
        val errorBody = response.body?.string() ?: "HTTP ${response.code}"
        emit(StreamingChunk(error = "Error ${response.code}: $errorBody"))
        response.close()
        return@flow
    }

    val reader = response.body?.byteStream()?.bufferedReader()
    if (reader == null) {
        emit(StreamingChunk(error = "Empty response body"))
        response.close()
        return@flow
    }

    val contentBuilder = StringBuilder()
    val reasoningBuilder = StringBuilder()
    val toolCallsMap = mutableMapOf<Int, MutableToolCallAccumulator>()
    var currentBlockType: String? = null
    var currentBlockIndex = 0
    var finishReason: String? = null
    var streamCompleted = false

    try {
        reader.useLines { lines ->
            loop@ for (line in lines) {
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue

                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    streamCompleted = true
                    break
                }

                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    val eventType = json.get("type")?.asString ?: continue

                    when (eventType) {
                        "message_stop" -> {
                            Log.d(TAG, "Anthropic stream: message_stop received")
                            streamCompleted = true
                            break@loop
                        }
                        "content_block_start" -> {
                            currentBlockIndex = json.get("index")?.asInt ?: 0
                            val block = json.getAsJsonObject("content_block")
                            currentBlockType = block?.get("type")?.asString
                            Log.d(TAG, "Anthropic stream: block_start index=$currentBlockIndex type=$currentBlockType")
                            if (currentBlockType == "tool_use") {
                                val acc = toolCallsMap.getOrPut(currentBlockIndex) {
                                    MutableToolCallAccumulator()
                                }
                                acc.id = block?.get("id")?.asString
                                acc.functionName = block?.get("name")?.asString
                            }
                        }
                        "content_block_delta" -> {
                            val delta = json.getAsJsonObject("delta") ?: continue
                            val deltaType = delta.get("type")?.asString

                            when (deltaType) {
                                "text_delta" -> {
                                    val text = delta.get("text")?.asString ?: ""
                                    contentBuilder.append(text)
                                    emit(StreamingChunk(content = text))
                                }
                                "thinking_delta" -> {
                                    val thinking = delta.get("thinking")?.asString ?: ""
                                    reasoningBuilder.append(thinking)
                                    emit(StreamingChunk(reasoning = thinking))
                                }
                                "input_json_delta" -> {
                                    val partial = delta.get("partial_json")?.asString ?: ""
                                    val acc = toolCallsMap.getOrPut(currentBlockIndex) {
                                        MutableToolCallAccumulator()
                                    }
                                    acc.argumentsBuilder.append(partial)
                                }
                            }
                        }
                        "message_delta" -> {
                            val delta = json.getAsJsonObject("delta")
                            finishReason = delta?.get("stop_reason")?.asString
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing Anthropic SSE: ${e.message}")
                }
            }
        }
    } catch (e: java.net.SocketException) {
        // Server closed connection after message_stop — this is normal
        if (!streamCompleted) {
            Log.e(TAG, "SocketException before stream completed", e)
            emit(StreamingChunk(error = "Stream error: ${e.message}"))
            return@flow
        }
        Log.d(TAG, "Anthropic stream: connection closed after completion (expected)")
    } catch (e: Exception) {
        if (!streamCompleted) {
            Log.e(TAG, "Error reading Anthropic stream", e)
            emit(StreamingChunk(error = "Stream error: ${e.message}"))
            return@flow
        }
        Log.d(TAG, "Anthropic stream: error after completion (ignored): ${e.message}")
    } finally {
        try { response.close() } catch (_: Exception) {}
    }

    val toolCalls = if (toolCallsMap.isNotEmpty()) {
        toolCallsMap.entries.sortedBy { it.key }.map { (_, acc) ->
            ToolCall(
                id = acc.id ?: "call_${System.currentTimeMillis()}",
                type = "function",
                function = ToolFunction(
                    name = acc.functionName ?: "",
                    arguments = acc.argumentsBuilder.toString()
                )
            )
        }
    } else null

    emit(StreamingChunk(
        done = true,
        toolCalls = toolCalls,
        finishReason = finishReason
    ))
}.flowOn(Dispatchers.IO)

private class MutableToolCallAccumulator {
    var id: String? = null
    var functionName: String? = null
    val argumentsBuilder = StringBuilder()
}

/**
 * Parses XML-style tool calls emitted by some models (MiniMax, certain OpenRouter models).
 *
 * Supported formats:
 * ```
 * <minimax:tool_call>
 *   <invoke name="tool_name">
 *     <parameter name="key">value</parameter>
 *   </invoke>
 * </minimax:tool_call>
 * ```
 * or just `<invoke name="...">...</invoke>` without the wrapper.
 *
 * Multiple `<invoke>` blocks are parsed as separate parallel tool calls.
 */
internal fun parseXmlToolCalls(content: String): List<ToolCall> {
    val invokePattern = Regex("""<invoke\s+name="([^"]+)">(.*?)</invoke>""", RegexOption.DOT_MATCHES_ALL)
    val paramPattern = Regex("""<parameter\s+name="([^"]+)">(.*?)</parameter>""", RegexOption.DOT_MATCHES_ALL)

    val results = mutableListOf<ToolCall>()

    for (match in invokePattern.findAll(content)) {
        val toolName = match.groupValues[1].trim()
        val body = match.groupValues[2]

        val params = mutableMapOf<String, Any>()
        for (paramMatch in paramPattern.findAll(body)) {
            val key = paramMatch.groupValues[1].trim()
            val value = paramMatch.groupValues[2]
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
            params[key] = value
        }

        val argsJson = com.google.gson.Gson().toJson(params)

        results.add(
            ToolCall(
                id = "call_${System.currentTimeMillis()}_${results.size}",
                type = "function",
                function = ToolFunction(
                    name = toolName,
                    arguments = argsJson
                )
            )
        )
    }

    if (results.isNotEmpty()) {
        Log.d(TAG, "Parsed ${results.size} XML tool call(s): ${results.joinToString { it.function.name }}")
    }
    return results
}

/**
 * Transforms a StreamingChunk flow to detect <think>...</think> tags in content
 * and redirect them to reasoning chunks in real-time.
 *
 * Models like Kimi k2p5 embed reasoning inside <think> tags in the content stream.
 * Without this, the user sees raw <think> tags during streaming, which are only
 * extracted to reasoning AFTER the stream completes — no real-time reasoning display.
 *
 * State machine:
 * - NORMAL: emitting content normally, watching for "<think>" opening tag
 * - BUFFERING: accumulating characters that might be part of "<think>" tag
 * - THINKING: inside <think> block, emitting as reasoning, watching for "</think>"
 * - CLOSE_BUFFERING: accumulating characters that might be part of "</think>" tag
 */
fun Flow<StreamingChunk>.withRealtimeThinkTagParsing(): Flow<StreamingChunk> = flow {
    val OPEN_TAG = "<think>"
    val CLOSE_TAG = "</think>"

    var state = ThinkParseState.NORMAL
    val buffer = StringBuilder()

    collect { chunk ->
        // Pass through non-content chunks (reasoning, tool calls, errors, done)
        if (chunk.content == null || chunk.done || chunk.error != null || chunk.toolCalls != null) {
            emit(chunk)
            return@collect
        }

        val text = chunk.content
        for (char in text) {
            when (state) {
                ThinkParseState.NORMAL -> {
                    if (char == '<') {
                        buffer.clear()
                        buffer.append(char)
                        state = ThinkParseState.BUFFERING
                    } else {
                        emit(StreamingChunk(content = char.toString()))
                    }
                }
                ThinkParseState.BUFFERING -> {
                    buffer.append(char)
                    val bufStr = buffer.toString()
                    if (bufStr == OPEN_TAG) {
                        // Matched <think>, switch to thinking mode
                        state = ThinkParseState.THINKING
                        buffer.clear()
                    } else if (!OPEN_TAG.startsWith(bufStr)) {
                        // Not a prefix of <think>, flush buffer as content
                        emit(StreamingChunk(content = bufStr))
                        buffer.clear()
                        state = ThinkParseState.NORMAL
                    }
                    // else: still a valid prefix, keep buffering
                }
                ThinkParseState.THINKING -> {
                    if (char == '<') {
                        buffer.clear()
                        buffer.append(char)
                        state = ThinkParseState.CLOSE_BUFFERING
                    } else {
                        emit(StreamingChunk(reasoning = char.toString()))
                    }
                }
                ThinkParseState.CLOSE_BUFFERING -> {
                    buffer.append(char)
                    val bufStr = buffer.toString()
                    if (bufStr == CLOSE_TAG) {
                        // Matched </think>, switch back to normal content
                        state = ThinkParseState.NORMAL
                        buffer.clear()
                    } else if (!CLOSE_TAG.startsWith(bufStr)) {
                        // Not a prefix of </think>, flush buffer as reasoning
                        emit(StreamingChunk(reasoning = bufStr))
                        buffer.clear()
                        state = ThinkParseState.THINKING
                    }
                    // else: still a valid prefix, keep buffering
                }
            }
        }
    }

    // Flush any remaining buffer
    if (buffer.isNotEmpty()) {
        when (state) {
            ThinkParseState.NORMAL, ThinkParseState.BUFFERING ->
                emit(StreamingChunk(content = buffer.toString()))
            ThinkParseState.THINKING, ThinkParseState.CLOSE_BUFFERING ->
                emit(StreamingChunk(reasoning = buffer.toString()))
        }
    }
}

private enum class ThinkParseState {
    NORMAL,          // Emitting content, watching for <think>
    BUFFERING,       // Might be start of <think> tag
    THINKING,        // Inside <think>, emitting as reasoning
    CLOSE_BUFFERING  // Might be start of </think> tag
}
