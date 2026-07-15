package com.aiagents.app.data.terminal

import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.domain.model.WebSearchProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * Unified search and page extraction.
 * Native HTML search is the default; API providers are used only after explicit selection.
 */
@Singleton
class UnifiedWebToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences,
    private val nativeSearch: DuckDuckGoSearchToolHandler,
    private val braveSearch: BraveSearchToolHandler,
    private val serpAPI: SerpAPIToolHandler
) {
    companion object {
        const val TOOL_SEARCH = "web_search"
        const val TOOL_FETCH = "web_fetch"
        val ALL_TOOL_NAMES = setOf(TOOL_SEARCH, TOOL_FETCH)
        private const val MAX_FETCH_BYTES = 1_500_000L
        private const val MAX_OUTPUT_CHARS = 30_000
        private const val MAX_REDIRECTS = 5

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_SEARCH,
                    "description" to "Busca información actual en Internet. Usa extracción HTML nativa sin API por defecto; Brave o SerpAPI solo cuando el usuario los selecciona en configuración. Devuelve títulos, fragmentos y URLs.",
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

    fun selectedProvider(): WebSearchProvider = securePreferences.getWebSearchProvider()

    fun isSearchConfigured(): Boolean = when (selectedProvider()) {
        WebSearchProvider.NATIVE -> true
        WebSearchProvider.BRAVE -> securePreferences.hasBraveApiKey()
        WebSearchProvider.SERPAPI -> securePreferences.hasSerpApiKey()
    }

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

        return when (selectedProvider()) {
            WebSearchProvider.NATIVE -> {
                val effectiveQuery = location?.let { "$query $it" } ?: query
                val nativeArgs = JsonObject().apply {
                    addProperty("query", effectiveQuery)
                    addProperty("count", num)
                }
                val result = nativeSearch.executeTool(toolCallId, gson.toJson(nativeArgs))
                UnifiedWebResult(
                    toolCallId,
                    TOOL_SEARCH,
                    result.success,
                    "Proveedor: Nativo (DuckDuckGo HTML)\n${result.content}"
                )
            }

            WebSearchProvider.BRAVE -> {
                val apiKey = securePreferences.getBraveApiKey()?.takeIf { it.isNotBlank() }
                    ?: return UnifiedWebResult(toolCallId, TOOL_SEARCH, false, "Brave está seleccionado pero no tiene una API key configurada.")
                val braveArgs = JsonObject().apply {
                    addProperty("query", location?.let { "$query $it" } ?: query)
                    addProperty("count", num)
                }
                val result = braveSearch.executeTool(toolCallId, gson.toJson(braveArgs), apiKey)
                UnifiedWebResult(toolCallId, TOOL_SEARCH, result.success, "Proveedor: Brave Search\n${result.content}")
            }

            WebSearchProvider.SERPAPI -> {
                val apiKey = securePreferences.getSerpApiKey()?.takeIf { it.isNotBlank() }
                    ?: return UnifiedWebResult(toolCallId, TOOL_SEARCH, false, "SerpAPI está seleccionado pero no tiene una API key configurada.")
                val serpArgs = JsonObject().apply {
                    addProperty("query", query)
                    addProperty("engine", "google")
                    addProperty("num", num)
                    location?.let { addProperty("location", it) }
                }
                val result = serpAPI.executeTool(toolCallId, gson.toJson(serpArgs), apiKey)
                UnifiedWebResult(toolCallId, TOOL_SEARCH, result.success, "Proveedor: SerpAPI\n${result.content}")
            }
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
