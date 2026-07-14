package com.aiagents.app.data.terminal

import com.aiagents.app.data.auth.ProviderCredentialResolver
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UnifiedWebResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String
)

/**
 * Search route independent from DuckDuckGo and Brave.
 * Uses SerpAPI when configured, otherwise OpenAI's supported Responses web_search tool.
 */
@Singleton
class UnifiedWebToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences,
    private val providerCredentialResolver: ProviderCredentialResolver,
    private val serpAPI: SerpAPIToolHandler
) {
    companion object {
        const val TOOL_SEARCH = "web_search"
        const val TOOL_FETCH = "web_fetch"
        val ALL_TOOL_NAMES = setOf(TOOL_SEARCH, TOOL_FETCH)
        private const val MAX_FETCH_BYTES = 1_500_000L
        private const val MAX_OPENAI_RESPONSE_BYTES = 2_000_000L
        private const val MAX_OUTPUT_CHARS = 30_000
        private const val MAX_REDIRECTS = 5

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_SEARCH,
                    "description" to "Busca información actual en Internet sin DuckDuckGo ni Brave. Selecciona automáticamente SerpAPI o la búsqueda web nativa de OpenAI según la configuración disponible. Devuelve texto y URLs de fuentes.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf("type" to "string", "description" to "Consulta de búsqueda"),
                            "num" to mapOf("type" to "integer", "description" to "Cantidad aproximada de resultados, 1-10"),
                            "location" to mapOf("type" to "string", "description" to "Ubicación opcional para contextualizar resultados")
                        ),
                        "required" to listOf("query")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_FETCH,
                    "description" to "Lee una página web pública por URL y extrae texto y enlaces. Bloquea localhost, redes privadas, respuestas demasiado grandes y redirecciones inseguras.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "url" to mapOf("type" to "string", "description" to "URL pública http/https"),
                            "max_chars" to mapOf("type" to "integer", "description" to "Máximo de caracteres de texto, 1000-30000")
                        ),
                        "required" to listOf("url")
                    )
                )
            )
        )
    }

    private val gson = Gson()

    fun isSearchConfigured(): Boolean =
        securePreferences.hasSerpApiKey() || providerCredentialResolver.resolveOpenAI() != null

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): UnifiedWebResult =
        when (toolName) {
            TOOL_SEARCH -> search(toolCallId, arguments)
            TOOL_FETCH -> fetch(toolCallId, arguments)
            else -> UnifiedWebResult(toolCallId, toolName, false, "Herramienta web desconocida: $toolName")
        }

    private suspend fun search(toolCallId: String, arguments: String): UnifiedWebResult {
        val args = parseArguments(toolCallId, TOOL_SEARCH, arguments) ?: return invalidArguments(toolCallId, TOOL_SEARCH)
        val query = args.safeString("query").orEmpty()
        if (query.isBlank()) return UnifiedWebResult(toolCallId, TOOL_SEARCH, false, "Falta el parámetro 'query'.")
        val num = args.safeInt("num")?.coerceIn(1, 10) ?: 5
        val location = args.safeString("location")

        val serpKey = securePreferences.getSerpApiKey()?.takeIf { it.isNotBlank() }
        if (serpKey != null) {
            val serpArgs = JsonObject().apply {
                addProperty("query", query)
                addProperty("engine", "google")
                addProperty("num", num)
                location?.let { addProperty("location", it) }
            }
            val result = serpAPI.executeTool(toolCallId, gson.toJson(serpArgs), serpKey)
            return UnifiedWebResult(
                toolCallId,
                TOOL_SEARCH,
                result.success,
                "Proveedor: SerpAPI\n${result.content}"
            )
        }

        val credentials = providerCredentialResolver.resolveOpenAI()
        if (credentials != null) {
            return searchWithOpenAI(
                toolCallId,
                query,
                num,
                location,
                credentials.apiKey,
                requireNotNull(credentials.baseUrl)
            )
        }

        return UnifiedWebResult(
            toolCallId,
            TOOL_SEARCH,
            false,
            "Búsqueda web no configurada. Agrega una API key de SerpAPI o una API key de OpenAI. Esta ruta no usa DuckDuckGo ni Brave."
        )
    }

    private suspend fun searchWithOpenAI(
        toolCallId: String,
        query: String,
        num: Int,
        location: String?,
        apiKey: String,
        baseUrl: String
    ): UnifiedWebResult = withContext(Dispatchers.IO) {
        try {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            val input = buildString {
                append("Search the web for: ")
                append(query)
                append(". Return a concise answer with up to $num relevant results and retain source URLs.")
                if (location != null) append(" Prefer results relevant to $location.")
            }
            val body = JsonObject().apply {
                addProperty("model", selectedOpenAIWebSearchModel())
                add("tools", JsonArray().apply {
                    add(JsonObject().apply { addProperty("type", "web_search") })
                })
                add("tool_choice", JsonObject().apply { addProperty("type", "web_search") })
                add("include", JsonArray().apply { add("web_search_call.action.sources") })
                addProperty("input", input)
                addProperty("max_output_tokens", 1_500)
            }
            val requestBuilder = Request.Builder()
                .url("$normalizedBaseUrl/responses")
                .header("Content-Type", "application/json")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
            val request = requestBuilder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val raw = readBoundedBody(response.body, MAX_OPENAI_RESPONSE_BYTES)
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JsonParser.parseString(raw).asJsonObject
                            .getAsJsonObject("error")?.get("message")?.asString
                    }.getOrNull() ?: "HTTP ${response.code}"
                    return@withContext UnifiedWebResult(toolCallId, TOOL_SEARCH, false, "OpenAI web search: $message")
                }
                val json = JsonParser.parseString(raw)
                if (!containsObjectType(json, "web_search_call")) {
                    return@withContext UnifiedWebResult(
                        toolCallId,
                        TOOL_SEARCH,
                        false,
                        "El proveedor respondió sin ejecutar la búsqueda web requerida."
                    )
                }
                val text = extractResponseText(json)
                val sources = linkedMapOf<String, String>()
                collectUrls(json, sources)
                val sourceBlock = sources.entries.take(20).joinToString("\n") { (url, title) ->
                    "- ${title.ifBlank { url }}: $url"
                }
                val content = buildString {
                    appendLine("Proveedor: OpenAI Responses web_search")
                    append(text.ifBlank { "La búsqueda terminó sin texto de respuesta." })
                    if (sourceBlock.isNotBlank()) {
                        appendLine()
                        appendLine()
                        appendLine("Fuentes:")
                        append(sourceBlock)
                    }
                }
                UnifiedWebResult(toolCallId, TOOL_SEARCH, true, content)
            }
        } catch (e: Exception) {
            UnifiedWebResult(toolCallId, TOOL_SEARCH, false, "Error en OpenAI web search: ${e.message}")
        }
    }

    private suspend fun fetch(toolCallId: String, arguments: String): UnifiedWebResult = withContext(Dispatchers.IO) {
        val args = parseArguments(toolCallId, TOOL_FETCH, arguments)
            ?: return@withContext invalidArguments(toolCallId, TOOL_FETCH)
        val rawUrl = args.safeString("url").orEmpty()
        val maxChars = args.safeInt("max_chars")?.coerceIn(1_000, MAX_OUTPUT_CHARS) ?: 15_000
        val initialUrl = WebUrlPolicy.validateUrl(rawUrl).getOrElse {
            return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, it.message ?: "URL no permitida")
        }

        try {
            val pinnedDns = mutableMapOf<String, List<InetAddress>>()
            val client = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        pinnedDns.getOrPut(hostname) { WebUrlPolicy.resolvePublic(hostname) }
                })
                .build()

            var currentUrl: HttpUrl = initialUrl
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                WebUrlPolicy.resolvePublic(currentUrl.host).also { pinnedDns[currentUrl.host] = it }
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "CortexAndroid/0.2.0 (+safe-web-fetch)")
                    .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json;q=0.8")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code in 300..399) {
                        if (redirectCount >= MAX_REDIRECTS) {
                            return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "Demasiadas redirecciones")
                        }
                        val location = response.header("Location")
                            ?: return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "Redirección sin destino")
                        val next = currentUrl.resolve(location)
                            ?: return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "Redirección inválida")
                        currentUrl = WebUrlPolicy.validateUrl(next.toString()).getOrElse {
                            return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "Redirección bloqueada: ${it.message}")
                        }
                        return@use
                    }
                    if (!response.isSuccessful) {
                        return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "La página respondió HTTP ${response.code}")
                    }
                    val body = response.body
                        ?: return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "La respuesta está vacía")
                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_FETCH_BYTES) {
                        return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "La página excede el límite de ${MAX_FETCH_BYTES / 1_000_000.0} MB")
                    }
                    val mediaType = body.contentType()?.toString()?.lowercase().orEmpty()
                    val allowed = mediaType.isBlank() || mediaType.startsWith("text/") ||
                        mediaType.contains("json") || mediaType.contains("xhtml")
                    if (!allowed) {
                        return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "Tipo de contenido no permitido: $mediaType")
                    }
                    val bytes = readLimited(body.source(), MAX_FETCH_BYTES)
                        ?: return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, false, "La página excede el límite de tamaño")
                    val raw = bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                    val content = if (mediaType.contains("html") || raw.contains("<html", ignoreCase = true)) {
                        renderHtml(currentUrl, raw, maxChars)
                    } else {
                        "URL final: $currentUrl\n\n${raw.take(maxChars)}"
                    }
                    return@withContext UnifiedWebResult(toolCallId, TOOL_FETCH, true, content)
                }
            }
            UnifiedWebResult(toolCallId, TOOL_FETCH, false, "No se pudo completar la lectura")
        } catch (e: Exception) {
            UnifiedWebResult(toolCallId, TOOL_FETCH, false, "Error leyendo la página: ${e.message}")
        }
    }

    private fun renderHtml(url: HttpUrl, raw: String, maxChars: Int): String {
        val document = Jsoup.parse(raw, url.toString())
        document.select("script,style,noscript,svg,canvas,template").remove()
        val text = document.body()?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val links = document.select("a[href]").mapNotNull { link ->
            val href = link.absUrl("href").takeIf { it.startsWith("http://") || it.startsWith("https://") }
            href?.let { "- ${link.text().ifBlank { it }.take(120)}: $it" }
        }.distinct().take(20)
        return buildString {
            appendLine("Título: ${document.title().ifBlank { "Sin título" }}")
            appendLine("URL final: $url")
            appendLine()
            append(text.take(maxChars))
            if (links.isNotEmpty()) {
                appendLine()
                appendLine()
                appendLine("Enlaces relevantes:")
                append(links.joinToString("\n"))
            }
        }
    }

    private fun readLimited(source: okio.BufferedSource, maxBytes: Long): ByteArray? {
        val buffer = Buffer()
        var total = 0L
        while (total <= maxBytes) {
            val read = source.read(buffer, minOf(8_192L, maxBytes + 1 - total))
            if (read == -1L) return buffer.readByteArray()
            total += read
            if (total > maxBytes) return null
        }
        return null
    }

    private fun readBoundedBody(body: ResponseBody?, maxBytes: Long): String {
        if (body == null) return ""
        val declaredLength = body.contentLength()
        require(declaredLength <= maxBytes) { "La respuesta excede el límite permitido" }
        val bytes = readLimited(body.source(), maxBytes)
            ?: throw IllegalStateException("La respuesta excede el límite permitido")
        return bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
    }

    private fun selectedOpenAIWebSearchModel(): String = securePreferences.getSelectedModels()
        .asSequence()
        .filter { it.startsWith("OPENAI|") }
        .map { it.substringAfter('|').trim() }
        .firstOrNull(String::isNotEmpty)
        ?: "gpt-5"

    private fun containsObjectType(element: JsonElement, expectedType: String): Boolean = when {
        element.isJsonObject -> {
            val obj = element.asJsonObject
            obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString == expectedType ||
                obj.entrySet().any { containsObjectType(it.value, expectedType) }
        }
        element.isJsonArray -> element.asJsonArray.any { containsObjectType(it, expectedType) }
        else -> false
    }

    private fun extractResponseText(root: JsonElement): String {
        val output = root.asJsonObject.getAsJsonArray("output") ?: return ""
        return output.flatMap { item ->
            item.asJsonObject.getAsJsonArray("content")?.mapNotNull { part ->
                val obj = part.asJsonObject
                if (obj.get("type")?.asString == "output_text") obj.get("text")?.asString else null
            } ?: emptyList()
        }.joinToString("\n").trim()
    }

    private fun collectUrls(element: JsonElement, output: MutableMap<String, String>) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val url = obj.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                if (url?.startsWith("http") == true) {
                    output.putIfAbsent(url, obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
                }
                obj.entrySet().forEach { collectUrls(it.value, output) }
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectUrls(it, output) }
        }
    }

    private fun JsonObject.safeString(name: String): String? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf(String::isNotEmpty)
    }.getOrNull()

    private fun JsonObject.safeInt(name: String): Int? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt
    }.getOrNull()

    private fun parseArguments(toolCallId: String, toolName: String, arguments: String): JsonObject? =
        runCatching { JsonParser.parseString(arguments).asJsonObject }.getOrNull()

    private fun invalidArguments(toolCallId: String, toolName: String) =
        UnifiedWebResult(toolCallId, toolName, false, "Los argumentos deben ser un objeto JSON válido.")
}
