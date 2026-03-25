package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DuckDuckGoSearchResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class DuckDuckGoSearchToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "DuckDuckGoSearch"
        const val TOOL_NAME = "duckduckgo_search"
        private const val SEARCH_URL = "https://html.duckduckgo.com/html/"

        val ALL_TOOL_NAMES = setOf(TOOL_NAME)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to "Search the web via DuckDuckGo. Use for current information, news, prices, or any up-to-date data. No API key required.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Search query"
                            ),
                            "count" to mapOf(
                                "type" to "integer",
                                "description" to "Number of results to return (default 5, max 15)"
                            )
                        ),
                        "required" to listOf("query")
                    )
                )
            )
        )

        // Regex patterns for html.duckduckgo.com/html/ results page
        private val RESULT_LINK_REGEX = Regex(
            """<a[^>]*rel="nofollow"[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        private val SNIPPET_REGEX = Regex(
            """<a[^>]*class="result__snippet"[^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        // Fallback: broader pattern for result links
        private val RESULT_LINK_FALLBACK_REGEX = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        // Another fallback: any link inside result__title
        private val RESULT_TITLE_LINK_REGEX = Regex(
            """<h2[^>]*class="result__title"[^>]*>[\s\S]*?<a[^>]*href="([^"]*)"[^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        private val SNIPPET_FALLBACK_REGEX = Regex(
            """<div[^>]*class="result__snippet"[^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )
    }

    // Dedicated client that follows redirects and handles cookies
    private val searchClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cookieJar(InMemoryCookieJar())
            .build()
    }

    suspend fun executeTool(
        toolCallId: String,
        arguments: String
    ): DuckDuckGoSearchResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val query = args.get("query")?.asString
                ?: return DuckDuckGoSearchResult(toolCallId, false, "Error: parámetro 'query' requerido")
            val count = args.get("count")?.asInt?.coerceIn(1, 15) ?: 5

            Log.d(TAG, "Searching DuckDuckGo: query='$query', count=$count")

            val body = withContext(Dispatchers.IO) {
                performSearch(query)
            }

            if (body == null) {
                return DuckDuckGoSearchResult(toolCallId, false, "Error: No se pudo conectar con DuckDuckGo")
            }

            Log.d(TAG, "Response length: ${body.length}, first 500 chars: ${body.take(500)}")

            val results = parseResults(body, count)

            if (results.isEmpty()) {
                // Log a snippet to debug
                Log.w(TAG, "No results parsed. HTML contains 'result__a': ${body.contains("result__a")}, " +
                        "'result-link': ${body.contains("result-link")}, " +
                        "'result__title': ${body.contains("result__title")}, " +
                        "body length: ${body.length}")
                return DuckDuckGoSearchResult(toolCallId, true, "No se encontraron resultados para: \"$query\"")
            }

            val formatted = buildString {
                appendLine("Resultados de búsqueda para: \"$query\"")
                appendLine()
                results.forEachIndexed { index, (title, resultUrl, snippet) ->
                    appendLine("${index + 1}. **$title**")
                    if (resultUrl.isNotBlank()) appendLine("   URL: $resultUrl")
                    if (snippet.isNotBlank()) appendLine("   $snippet")
                    appendLine()
                }
            }

            DuckDuckGoSearchResult(toolCallId, true, formatted.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error searching DuckDuckGo", e)
            DuckDuckGoSearchResult(toolCallId, false, "Error al buscar: ${e.message}")
        }
    }

    private fun performSearch(query: String): String? {
        try {
            // Step 1: GET the homepage first to establish cookies/session
            val initRequest = Request.Builder()
                .url("https://html.duckduckgo.com/")
                .get()
                .addHeader("User-Agent", getUserAgent())
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
                .addHeader("Accept-Encoding", "identity")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .build()

            val initResp = searchClient.newCall(initRequest).execute()
            initResp.body?.string() // consume the body
            initResp.close()
            Log.d(TAG, "Init request completed with code: ${initResp.code}")

            // Step 2: POST the search query
            val formBody = FormBody.Builder()
                .add("q", query)
                .add("b", "")
                .add("kl", "")
                .add("df", "")
                .build()

            val searchRequest = Request.Builder()
                .url(SEARCH_URL)
                .post(formBody)
                .addHeader("User-Agent", getUserAgent())
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
                .addHeader("Accept-Encoding", "identity")
                .addHeader("Referer", "https://html.duckduckgo.com/")
                .addHeader("Origin", "https://html.duckduckgo.com")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val resp = searchClient.newCall(searchRequest).execute()
            val responseBody = resp.body?.string()
            Log.d(TAG, "Search response code: ${resp.code}")

            if (resp.code !in 200..299) {
                Log.w(TAG, "Non-success HTTP code: ${resp.code}")
                return null
            }

            // Check for CAPTCHA / bot challenge
            if (responseBody != null && (
                responseBody.contains("d.getElementById('challenge-form')", ignoreCase = true) ||
                responseBody.contains("Please try again", ignoreCase = true) ||
                responseBody.contains("select all squares", ignoreCase = true)
            )) {
                Log.w(TAG, "DuckDuckGo returned a CAPTCHA challenge, trying lite endpoint...")
                return tryLiteEndpoint(query)
            }

            return responseBody
        } catch (e: Exception) {
            Log.e(TAG, "Error performing search", e)
            return null
        }
    }

    private fun tryLiteEndpoint(query: String): String? {
        try {
            val formBody = FormBody.Builder()
                .add("q", query)
                .build()

            val request = Request.Builder()
                .url("https://lite.duckduckgo.com/lite/")
                .post(formBody)
                .addHeader("User-Agent", getUserAgent())
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
                .addHeader("Accept-Encoding", "identity")
                .addHeader("Referer", "https://lite.duckduckgo.com/")
                .addHeader("Origin", "https://lite.duckduckgo.com")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .build()

            val resp = searchClient.newCall(request).execute()
            val body = resp.body?.string()
            Log.d(TAG, "Lite search response code: ${resp.code}, body length: ${body?.length}")
            return body
        } catch (e: Exception) {
            Log.e(TAG, "Lite endpoint also failed", e)
            return null
        }
    }

    private fun getUserAgent(): String {
        // Rotate through common desktop User-Agents to reduce fingerprinting
        val agents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:125.0) Gecko/20100101 Firefox/125.0"
        )
        return agents.random()
    }

    private fun parseResults(html: String, maxResults: Int): List<Triple<String, String, String>> {
        // Try primary patterns (html.duckduckgo.com/html/)
        var links = RESULT_LINK_REGEX.findAll(html).toList()
        var snippets = SNIPPET_REGEX.findAll(html).toList()

        // Fallback patterns
        if (links.isEmpty()) {
            links = RESULT_LINK_FALLBACK_REGEX.findAll(html).toList()
        }
        if (links.isEmpty()) {
            links = RESULT_TITLE_LINK_REGEX.findAll(html).toList()
        }
        if (snippets.isEmpty()) {
            snippets = SNIPPET_FALLBACK_REGEX.findAll(html).toList()
        }

        // Try lite.duckduckgo.com patterns if still nothing
        if (links.isEmpty()) {
            return parseLiteResults(html, maxResults)
        }

        val results = mutableListOf<Triple<String, String, String>>()
        for (i in links.indices) {
            if (results.size >= maxResults) break
            val rawUrl = links[i].groupValues[1]
            val title = stripHtmlTags(links[i].groupValues[2]).trim()
            if (title.isBlank()) continue
            val url = decodeDdgUrl(rawUrl)
            val snippet = if (i < snippets.size) stripHtmlTags(snippets[i].groupValues[1]).trim() else ""
            results.add(Triple(title, url, snippet))
        }
        return results
    }

    private fun parseLiteResults(html: String, maxResults: Int): List<Triple<String, String, String>> {
        // Lite page patterns
        val liteLink = Regex(
            """<a[^>]*href="([^"]+)"[^>]*class=['"]result-link['"][^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val liteSnippet = Regex(
            """<td[^>]*class=['"]result-snippet['"][^>]*>([\s\S]*?)</td>""",
            RegexOption.IGNORE_CASE
        )
        // Remove sponsored results
        val cleanHtml = Regex(
            """<tr[^>]*class=["']result-sponsored["'][^>]*>[\s\S]*?</tr>""",
            RegexOption.IGNORE_CASE
        ).replace(html, "")

        val links = liteLink.findAll(cleanHtml).toList()
        val snippets = liteSnippet.findAll(cleanHtml).toList()

        val results = mutableListOf<Triple<String, String, String>>()
        for (i in links.indices) {
            if (results.size >= maxResults) break
            val rawUrl = links[i].groupValues[1]
            val title = stripHtmlTags(links[i].groupValues[2]).trim()
            if (title.isBlank()) continue
            val url = decodeDdgUrl(rawUrl)
            val snippet = if (i < snippets.size) stripHtmlTags(snippets[i].groupValues[1]).trim() else ""
            results.add(Triple(title, url, snippet))
        }
        return results
    }

    private fun decodeDdgUrl(rawUrl: String): String {
        // DuckDuckGo wraps URLs in redirect: //duckduckgo.com/l/?uddg=ENCODED_URL&...
        if (rawUrl.contains("uddg=")) {
            val encoded = rawUrl.substringAfter("uddg=").substringBefore("&")
            return try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                rawUrl
            }
        }
        return rawUrl
    }

    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
    }
}
