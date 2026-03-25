package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class BraveSearchResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class BraveSearchToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "BraveSearchToolHandler"
        const val TOOL_NAME = "brave_web_search"
        private const val API_URL = "https://api.search.brave.com/res/v1/web/search"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME,
                        "description" to "Search the web via Brave Search. Use for current information, news, prices, or any up-to-date data.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf(
                                    "type" to "string",
                                    "description" to "Search query"
                                ),
                                "count" to mapOf(
                                    "type" to "integer",
                                    "description" to "Número de resultados a devolver (por defecto 5, máximo 20)"
                                )
                            ),
                            "required" to listOf("query")
                        )
                    )
                )
            )
        }
    }

    suspend fun executeTool(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): BraveSearchResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val query = args.get("query")?.asString
                ?: return BraveSearchResult(toolCallId, false, "Error: parámetro 'query' requerido")
            val count = args.get("count")?.asInt?.coerceIn(1, 20) ?: 5

            Log.d(TAG, "Ejecutando Brave Search: query='$query', count=$count")

            val url = "$API_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&count=$count"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("X-Subscription-Token", apiKey)
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "Brave Search error: $responseCode - $body")
                return BraveSearchResult(toolCallId, false, "Error HTTP $responseCode al buscar en Brave Search")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val results = json.getAsJsonObject("web")?.getAsJsonArray("results")

            if (results == null || results.size() == 0) {
                return BraveSearchResult(toolCallId, true, "No se encontraron resultados para: \"$query\"")
            }

            val formatted = buildString {
                appendLine("Resultados de búsqueda para: \"$query\"")
                appendLine()
                results.forEachIndexed { index, item ->
                    val r = item.asJsonObject
                    val title = r.get("title")?.asString ?: "(sin título)"
                    val url2 = r.get("url")?.asString ?: ""
                    val description = r.get("description")?.asString ?: ""
                    appendLine("${index + 1}. **$title**")
                    if (url2.isNotBlank()) appendLine("   URL: $url2")
                    if (description.isNotBlank()) appendLine("   $description")
                    appendLine()
                }
            }

            BraveSearchResult(toolCallId, true, formatted.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando Brave Search", e)
            BraveSearchResult(toolCallId, false, "Error al buscar: ${e.message}")
        }
    }
}
