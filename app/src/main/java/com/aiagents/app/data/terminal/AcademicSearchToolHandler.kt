package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class AcademicSearchResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class AcademicSearchToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AcademicSearchToolHandler"
        const val TOOL_NAME_WIKIPEDIA = "wikipedia_search"
        const val TOOL_NAME_ARXIV = "arxiv_search"

        val ALL_TOOL_NAMES = setOf(TOOL_NAME_WIKIPEDIA, TOOL_NAME_ARXIV)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_WIKIPEDIA,
                    "description" to "Search Wikipedia for general knowledge, encyclopedia articles, definitions, historical facts, and overview information. Use for: general topics, biographies, definitions, historical events, science concepts, technology overviews.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Search query or article title to look up"
                            ),
                            "language" to mapOf(
                                "type" to "string",
                                "description" to "Language code (default: es for Spanish, use 'en' for English)",
                                "enum" to listOf("es", "en", "fr", "de", "pt", "it")
                            ),
                            "num_results" to mapOf(
                                "type" to "integer",
                                "description" to "Number of results to return (default 3, max 10)"
                            )
                        ),
                        "required" to listOf("query")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_ARXIV,
                    "description" to "Search arXiv for academic papers, research articles, preprints in physics, mathematics, computer science, quantitative biology, quantitative finance, statistics, and electrical engineering. Use for: scientific research, technical papers, algorithms, mathematical proofs, cutting-edge CS research.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Search query for papers (supports boolean: AND, OR, NOT)"
                            ),
                            "category" to mapOf(
                                "type" to "string",
                                "description" to "arXiv category filter (e.g., 'cs.AI', 'cs.LG', 'physics', 'math', 'q-bio')"
                            ),
                            "num_results" to mapOf(
                                "type" to "integer",
                                "description" to "Number of papers to return (default 5, max 20)"
                            ),
                            "sort_by" to mapOf(
                                "type" to "string",
                                "description" to "Sort order",
                                "enum" to listOf("relevance", "lastUpdatedDate", "submittedDate")
                            )
                        ),
                        "required" to listOf("query")
                    )
                )
            )
        )
    }

    suspend fun executeWikipediaSearch(
        toolCallId: String,
        arguments: String
    ): AcademicSearchResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val query = args.get("query")?.asString
                ?: return AcademicSearchResult(toolCallId, false, "Error: parámetro 'query' requerido")
            val language = args.get("language")?.asString ?: "es"
            val numResults = args.get("num_results")?.asInt?.coerceIn(1, 10) ?: 3

            Log.d(TAG, "Buscando en Wikipedia: query='$query', lang='$language'")

            // Primero buscar artículos relacionados
            val searchUrl = buildString {
                append("https://${language}.wikipedia.org/w/api.php")
                append("?action=query")
                append("&list=search")
                append("&srsearch=${java.net.URLEncoder.encode(query, "UTF-8")}")
                append("&srlimit=$numResults")
                append("&format=json")
                append("&origin=*")
            }

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("Accept", "application/json")
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(searchRequest).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "Wikipedia search error: $responseCode")
                return AcademicSearchResult(toolCallId, false, "Error HTTP $responseCode al buscar en Wikipedia")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val searchResults = json.getAsJsonObject("query")?.getAsJsonArray("search")

            if (searchResults == null || searchResults.size() == 0) {
                return AcademicSearchResult(toolCallId, true, "No se encontraron resultados en Wikipedia para: \"$query\"")
            }

            // Obtener extractos para cada resultado
            val formatted = buildString {
                appendLine("Resultados de Wikipedia para: \"$query\"")
                appendLine()

                searchResults.forEachIndexed { index, item ->
                    val result = item.asJsonObject
                    val title = result.get("title")?.asString ?: "(sin título)"
                    val pageId = result.get("pageid")?.asLong
                    val snippet = result.get("snippet")?.asString?.let { cleanWikiMarkup(it) } ?: ""

                    appendLine("${index + 1}. **$title**")

                    // Obtener extracto completo del artículo
                    if (pageId != null) {
                        val extract = fetchWikipediaExtract(pageId, language)
                        if (extract.isNotBlank()) {
                            appendLine("   $extract")
                        } else if (snippet.isNotBlank()) {
                            appendLine("   $snippet")
                        }
                    } else if (snippet.isNotBlank()) {
                        appendLine("   $snippet")
                    }

                    appendLine("   URL: https://${language}.wikipedia.org/wiki/${title.replace(" ", "_")}")
                    appendLine()
                }
            }

            AcademicSearchResult(toolCallId, true, formatted.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error en búsqueda de Wikipedia", e)
            AcademicSearchResult(toolCallId, false, "Error al buscar en Wikipedia: ${e.message}")
        }
    }

    private suspend fun fetchWikipediaExtract(pageId: Long, language: String): String {
        return try {
            val extractUrl = buildString {
                append("https://${language}.wikipedia.org/w/api.php")
                append("?action=query")
                append("&pageids=$pageId")
                append("&prop=extracts")
                append("&exintro=true")
                append("&exlimit=1")
                append("&explaintext=true")
                append("&format=json")
                append("&origin=*")
            }

            val request = Request.Builder()
                .url(extractUrl)
                .addHeader("Accept", "application/json")
                .build()

            val body = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().body?.string()
            } ?: return ""

            val json = JsonParser.parseString(body).asJsonObject
            val pages = json.getAsJsonObject("query")?.getAsJsonObject("pages")
            val page = pages?.getAsJsonObject(pageId.toString())
            val extract = page?.get("extract")?.asString ?: ""

            // Truncar si es muy largo
            if (extract.length > 500) {
                extract.take(500) + "..."
            } else {
                extract
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo extracto de Wikipedia", e)
            ""
        }
    }

    private fun cleanWikiMarkup(text: String): String {
        return text
            .replace(Regex("<span[^>]*class=\"searchmatch\"[^>]*>"), "**")
            .replace(Regex("</span>"), "**")
            .replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    suspend fun executeArxivSearch(
        toolCallId: String,
        arguments: String
    ): AcademicSearchResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val query = args.get("query")?.asString
                ?: return AcademicSearchResult(toolCallId, false, "Error: parámetro 'query' requerido")
            val category = args.get("category")?.asString
            val numResults = args.get("num_results")?.asInt?.coerceIn(1, 20) ?: 5
            val sortBy = args.get("sort_by")?.asString ?: "relevance"

            Log.d(TAG, "Buscando en arXiv: query='$query', category='$category'")

            // Construir query de arXiv
            val searchQuery = buildString {
                append(java.net.URLEncoder.encode(query, "UTF-8"))
                if (!category.isNullOrBlank()) {
                    append("+cat:")
                    append(java.net.URLEncoder.encode(category, "UTF-8"))
                }
            }

            val sortParam = when (sortBy) {
                "lastUpdatedDate" -> "lastUpdatedDate"
                "submittedDate" -> "submittedDate"
                else -> "relevance"
            }

            val url = buildString {
                append("http://export.arxiv.org/api/query")
                append("?search_query=$searchQuery")
                append("&start=0")
                append("&max_results=$numResults")
                append("&sortBy=$sortParam")
                append("&sortOrder=descending")
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/atom+xml")
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "arXiv error: $responseCode")
                return AcademicSearchResult(toolCallId, false, "Error HTTP $responseCode al buscar en arXiv")
            }

            val papers = parseArxivResponse(body)

            if (papers.isEmpty()) {
                return AcademicSearchResult(toolCallId, true, "No se encontraron papers en arXiv para: \"$query\"")
            }

            val formatted = buildString {
                appendLine("Papers de arXiv para: \"$query\"")
                appendLine()

                papers.forEachIndexed { index, paper ->
                    appendLine("${index + 1}. **${paper.title}**")
                    appendLine("   Autores: ${paper.authors.take(3).joinToString(", ")}${if (paper.authors.size > 3) " et al." else ""}")
                    appendLine("   Publicado: ${paper.publishedDate}")
                    if (paper.categories.isNotEmpty()) {
                        appendLine("   Categorías: ${paper.categories.joinToString(", ")}")
                    }
                    appendLine("   URL: ${paper.link}")
                    if (paper.pdfLink.isNotBlank()) {
                        appendLine("   PDF: ${paper.pdfLink}")
                    }
                    appendLine()
                    // Resumen truncado
                    val summary = if (paper.summary.length > 300) {
                        paper.summary.take(300) + "..."
                    } else {
                        paper.summary
                    }
                    appendLine("   Resumen: $summary")
                    appendLine()
                }
            }

            AcademicSearchResult(toolCallId, true, formatted.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error en búsqueda de arXiv", e)
            AcademicSearchResult(toolCallId, false, "Error al buscar en arXiv: ${e.message}")
        }
    }

    private fun parseArxivResponse(xml: String): List<ArxivPaper> {
        val papers = mutableListOf<ArxivPaper>()

        // Parse básico del XML (atom feed)
        val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val summaryRegex = Regex("<summary[^>]*>(.*?)</summary>", RegexOption.DOT_MATCHES_ALL)
        val authorRegex = Regex("<name>(.*?)</name>")
        val publishedRegex = Regex("<published>(.*?)</published>")
        val linkRegex = Regex("<link[^>]*href=\"([^\"]+)\"[^>]*>")
        val categoryRegex = Regex("<category[^>]*term=\"([^\"]+)\"[^>]*/>")

        entryRegex.findAll(xml).forEach { match ->
            val entry = match.groupValues[1]

            val title = titleRegex.find(entry)?.groupValues?.get(1)?.trim()?.replace("\n", " ")?.replace(Regex("\\s+"), " ") ?: ""
            val summary = summaryRegex.find(entry)?.groupValues?.get(1)?.trim()?.replace("\n", " ")?.replace(Regex("\\s+"), " ") ?: ""
            val authors = authorRegex.findAll(entry).map { it.groupValues[1].trim() }.toList()
            val published = publishedRegex.find(entry)?.groupValues?.get(1)?.take(10) ?: ""

            val links = linkRegex.findAll(entry).map { it.groupValues[1] }.toList()
            val mainLink = links.firstOrNull { !it.endsWith(".pdf") } ?: ""
            val pdfLink = links.firstOrNull { it.endsWith(".pdf") } ?: ""

            val categories = categoryRegex.findAll(entry).map { it.groupValues[1] }.toList()

            if (title.isNotBlank()) {
                papers.add(ArxivPaper(
                    title = cleanXmlEntities(title),
                    summary = cleanXmlEntities(summary),
                    authors = authors,
                    publishedDate = published,
                    link = mainLink,
                    pdfLink = pdfLink,
                    categories = categories
                ))
            }
        }

        return papers
    }

    private fun cleanXmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
    }

    data class ArxivPaper(
        val title: String,
        val summary: String,
        val authors: List<String>,
        val publishedDate: String,
        val link: String,
        val pdfLink: String,
        val categories: List<String>
    )
}
