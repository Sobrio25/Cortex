package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class PubMedToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class PubMedToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "PubMedToolHandler"
        private const val BASE_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"

        const val TOOL_SEARCH = "pubmed_search"
        const val TOOL_FETCH_ARTICLE = "pubmed_fetch_article"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_SEARCH,
                        "description" to "Busca artículos científicos y médicos en PubMed (base de datos de la Biblioteca Nacional de Medicina de EE.UU.). " +
                            "Úsala para encontrar estudios, revisiones sistemáticas, ensayos clínicos y evidencia médica. " +
                            "Devuelve títulos, autores, resúmenes y links a los artículos. Ideal para consejos de salud basados en evidencia.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf(
                                    "type" to "string",
                                    "description" to "Término de búsqueda médico/científico. Puede ser en inglés o español. Ejemplos: 'diabetes type 2 treatment', 'vitamin D deficiency', 'ejercicio cardiovascular beneficios'"
                                ),
                                "max_results" to mapOf(
                                    "type" to "integer",
                                    "description" to "Número máximo de artículos a devolver (por defecto 5, máximo 15)"
                                ),
                                "sort" to mapOf(
                                    "type" to "string",
                                    "description" to "Ordenar por: 'relevance' (relevancia, por defecto), 'date' (más recientes primero), 'pub_date' (fecha de publicación)"
                                )
                            ),
                            "required" to listOf("query")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_FETCH_ARTICLE,
                        "description" to "Obtiene el detalle completo de un artículo de PubMed por su PMID (PubMed ID). " +
                            "Incluye título, autores, abstract completo, journal, fecha de publicación y DOI. " +
                            "Usa esta herramienta después de buscar para obtener más detalles de un artículo específico.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "pmid" to mapOf(
                                    "type" to "string",
                                    "description" to "El PubMed ID (PMID) del artículo a consultar"
                                )
                            ),
                            "required" to listOf("pmid")
                        )
                    )
                )
            )
        }
    }

    suspend fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String
    ): PubMedToolResult {
        return try {
            when (toolName) {
                TOOL_SEARCH -> executeSearch(toolCallId, arguments)
                TOOL_FETCH_ARTICLE -> executeFetchArticle(toolCallId, arguments)
                else -> PubMedToolResult(toolCallId, false, "Error: herramienta '$toolName' no reconocida")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando herramienta PubMed: $toolName", e)
            PubMedToolResult(toolCallId, false, "Error al consultar PubMed: ${e.message}")
        }
    }

    private suspend fun executeSearch(
        toolCallId: String,
        arguments: String
    ): PubMedToolResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val query = args.get("query")?.asString
            ?: return PubMedToolResult(toolCallId, false, "Error: parámetro 'query' requerido")
        val maxResults = args.get("max_results")?.asInt?.coerceIn(1, 15) ?: 5
        val sort = args.get("sort")?.asString ?: "relevance"

        Log.d(TAG, "Buscando en PubMed: query='$query', max=$maxResults, sort=$sort")

        // Step 1: Search for PMIDs
        val sortParam = when (sort) {
            "date" -> "&sort=date"
            "pub_date" -> "&sort=pub+date"
            else -> "&sort=relevance"
        }
        val searchUrl = "$BASE_URL/esearch.fcgi?db=pubmed&term=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&retmode=json&retmax=$maxResults$sortParam"

        val searchRequest = Request.Builder()
            .url(searchUrl)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val (searchCode, searchBody) = withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(searchRequest).execute()
            resp.code to (resp.body?.string() ?: "")
        }

        if (searchCode !in 200..299) {
            Log.e(TAG, "PubMed search error: $searchCode - $searchBody")
            return PubMedToolResult(toolCallId, false, "Error HTTP $searchCode al buscar en PubMed")
        }

        val searchJson = JsonParser.parseString(searchBody).asJsonObject
        val esearchResult = searchJson.getAsJsonObject("esearchresult")
        val idList = esearchResult?.getAsJsonArray("idlist")

        if (idList == null || idList.size() == 0) {
            return PubMedToolResult(toolCallId, true, "No se encontraron artículos en PubMed para: \"$query\"")
        }

        val pmids = idList.map { it.asString }
        val totalCount = esearchResult.get("count")?.asString ?: "?"

        // Step 2: Get summaries for all PMIDs
        val idsParam = pmids.joinToString(",")
        val summaryUrl = "$BASE_URL/esummary.fcgi?db=pubmed&id=$idsParam&retmode=json"

        val summaryRequest = Request.Builder()
            .url(summaryUrl)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val (summaryCode, summaryBody) = withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(summaryRequest).execute()
            resp.code to (resp.body?.string() ?: "")
        }

        if (summaryCode !in 200..299) {
            Log.e(TAG, "PubMed summary error: $summaryCode - $summaryBody")
            return PubMedToolResult(toolCallId, false, "Error HTTP $summaryCode al obtener resúmenes de PubMed")
        }

        val summaryJson = JsonParser.parseString(summaryBody).asJsonObject
        val result = summaryJson.getAsJsonObject("result")

        val formatted = buildString {
            appendLine("Resultados de PubMed para: \"$query\" ($totalCount artículos encontrados, mostrando ${pmids.size})")
            appendLine()

            pmids.forEachIndexed { index, pmid ->
                val article = result?.getAsJsonObject(pmid)
                if (article != null) {
                    val title = article.get("title")?.asString ?: "(sin título)"
                    val source = article.get("source")?.asString ?: ""
                    val pubDate = article.get("pubdate")?.asString ?: ""
                    val authors = article.getAsJsonArray("authors")
                    val authorNames = if (authors != null && authors.size() > 0) {
                        val names = authors.take(3).map {
                            it.asJsonObject.get("name")?.asString ?: ""
                        }.filter { it.isNotBlank() }
                        if (authors.size() > 3) "${names.joinToString(", ")} et al."
                        else names.joinToString(", ")
                    } else ""

                    val doi = article.getAsJsonArray("articleids")?.firstOrNull {
                        it.asJsonObject.get("idtype")?.asString == "doi"
                    }?.asJsonObject?.get("value")?.asString

                    appendLine("${index + 1}. **$title**")
                    if (authorNames.isNotBlank()) appendLine("   Autores: $authorNames")
                    if (source.isNotBlank()) appendLine("   Journal: $source")
                    if (pubDate.isNotBlank()) appendLine("   Fecha: $pubDate")
                    appendLine("   PMID: $pmid")
                    appendLine("   Link: https://pubmed.ncbi.nlm.nih.gov/$pmid/")
                    if (doi != null) appendLine("   DOI: https://doi.org/$doi")
                    appendLine()
                }
            }
        }.trim()

        return PubMedToolResult(toolCallId, true, formatted)
    }

    private suspend fun executeFetchArticle(
        toolCallId: String,
        arguments: String
    ): PubMedToolResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val pmid = args.get("pmid")?.asString
            ?: return PubMedToolResult(toolCallId, false, "Error: parámetro 'pmid' requerido")

        Log.d(TAG, "Obteniendo artículo PubMed: PMID=$pmid")

        // Get full abstract via efetch (XML format, more complete)
        val fetchUrl = "$BASE_URL/efetch.fcgi?db=pubmed&id=$pmid&retmode=xml"

        val fetchRequest = Request.Builder()
            .url(fetchUrl)
            .get()
            .build()

        val (fetchCode, fetchBody) = withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(fetchRequest).execute()
            resp.code to (resp.body?.string() ?: "")
        }

        if (fetchCode !in 200..299) {
            Log.e(TAG, "PubMed fetch error: $fetchCode - $fetchBody")
            return PubMedToolResult(toolCallId, false, "Error HTTP $fetchCode al obtener artículo de PubMed")
        }

        // Parse XML response to extract key fields
        val title = extractXmlTag(fetchBody, "ArticleTitle") ?: "(sin título)"
        val abstractText = extractAbstract(fetchBody)
        val journal = extractXmlTag(fetchBody, "Title") ?: ""
        val year = extractXmlTag(fetchBody, "Year") ?: ""
        val month = extractXmlTag(fetchBody, "Month") ?: ""
        val doi = extractDoi(fetchBody)

        // Extract authors
        val authorRegex = "<Author[^>]*>.*?<LastName>(.*?)</LastName>.*?<ForeName>(.*?)</ForeName>.*?</Author>"
            .toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
        val authors = authorRegex.findAll(fetchBody).map { match ->
            "${match.groupValues[2]} ${match.groupValues[1]}"
        }.toList()
        val authorStr = if (authors.size > 5) {
            "${authors.take(5).joinToString(", ")} et al."
        } else {
            authors.joinToString(", ")
        }

        // Extract keywords
        val keywordRegex = "<Keyword[^>]*>(.*?)</Keyword>".toRegex()
        val keywords = keywordRegex.findAll(fetchBody).map { it.groupValues[1] }.toList()

        val formatted = buildString {
            appendLine("**$title**")
            appendLine()
            if (authorStr.isNotBlank()) appendLine("**Autores:** $authorStr")
            if (journal.isNotBlank()) appendLine("**Journal:** $journal")
            if (year.isNotBlank()) appendLine("**Fecha:** ${if (month.isNotBlank()) "$month " else ""}$year")
            appendLine("**PMID:** $pmid")
            appendLine("**Link:** https://pubmed.ncbi.nlm.nih.gov/$pmid/")
            if (doi != null) appendLine("**DOI:** https://doi.org/$doi")
            appendLine()
            if (abstractText.isNotBlank()) {
                appendLine("**Abstract:**")
                appendLine(abstractText)
            } else {
                appendLine("(Abstract no disponible)")
            }
            if (keywords.isNotEmpty()) {
                appendLine()
                appendLine("**Palabras clave:** ${keywords.joinToString(", ")}")
            }
        }.trim()

        return PubMedToolResult(toolCallId, true, formatted)
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val regex = "<$tag[^>]*>(.*?)</$tag>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }

    private fun extractAbstract(xml: String): String {
        // Try structured abstract first (with labeled sections)
        val sectionRegex = "<AbstractText[^>]*Label=\"([^\"]*?)\"[^>]*>(.*?)</AbstractText>"
            .toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
        val sections = sectionRegex.findAll(xml).toList()

        if (sections.isNotEmpty()) {
            return sections.joinToString("\n\n") { match ->
                val label = match.groupValues[1]
                val text = match.groupValues[2].trim()
                "**$label:** $text"
            }
        }

        // Try simple abstract
        val simpleRegex = "<AbstractText>(.*?)</AbstractText>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return simpleRegex.find(xml)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractDoi(xml: String): String? {
        val doiRegex = "<ArticleId IdType=\"doi\">(.*?)</ArticleId>".toRegex()
        return doiRegex.find(xml)?.groupValues?.get(1)
    }
}
