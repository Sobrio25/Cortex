package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class NotionToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class NotionToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "NotionToolHandler"
        private const val API_URL = "https://api.notion.com/v1"
        private const val NOTION_VERSION = "2022-06-28"
        const val TOOL_SEARCH = "notion_search"
        const val TOOL_READ_PAGE = "notion_read_page"
        const val TOOL_CREATE_PAGE = "notion_create_page"
        const val TOOL_APPEND_BLOCKS = "notion_append_blocks"
        const val TOOL_LIST_DATABASES = "notion_list_databases"

        val ALL_TOOL_NAMES = setOf(
            TOOL_SEARCH, TOOL_READ_PAGE, TOOL_CREATE_PAGE,
            TOOL_APPEND_BLOCKS, TOOL_LIST_DATABASES
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SEARCH,
                "description" to "Busca paginas y bases de datos en Notion por texto.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Texto a buscar en Notion"),
                        "filter" to mapOf("type" to "string", "description" to "Filtrar por tipo: 'page' o 'database' (opcional)")
                    ), "required" to listOf("query"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_READ_PAGE,
                "description" to "Lee el contenido de una pagina de Notion por su ID. Devuelve titulo, propiedades y bloques de contenido.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "page_id" to mapOf("type" to "string", "description" to "ID de la pagina de Notion (32 caracteres hex, con o sin guiones)")
                    ), "required" to listOf("page_id"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_CREATE_PAGE,
                "description" to "Crea una nueva pagina en Notion. Puede ser hija de otra pagina o de una base de datos.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "parent_id" to mapOf("type" to "string", "description" to "ID de la pagina padre o base de datos donde crear la pagina"),
                        "parent_type" to mapOf("type" to "string", "description" to "'page' o 'database' (default: 'page')"),
                        "title" to mapOf("type" to "string", "description" to "Titulo de la nueva pagina"),
                        "content" to mapOf("type" to "string", "description" to "Contenido en texto. Se convierte automaticamente a bloques de Notion (parrafos, headers con #, bullets con -, code blocks)")
                    ), "required" to listOf("parent_id", "title"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_APPEND_BLOCKS,
                "description" to "Agrega contenido al final de una pagina existente de Notion.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "page_id" to mapOf("type" to "string", "description" to "ID de la pagina"),
                        "content" to mapOf("type" to "string", "description" to "Contenido a agregar (texto, headers #, bullets -, code blocks)")
                    ), "required" to listOf("page_id", "content"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST_DATABASES,
                "description" to "Lista las bases de datos disponibles en Notion a las que la integracion tiene acceso.",
                "parameters" to mapOf("type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>())
            ))
        )
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): NotionToolResult {
        val token = securePreferences.getNotionToken()
        if (token.isNullOrBlank()) {
            return NotionToolResult(toolCallId, false,
                "Error: Notion no esta configurado. Ve a MCP para agregar tu Integration Token.")
        }
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_SEARCH -> search(toolCallId, args, token)
                TOOL_READ_PAGE -> readPage(toolCallId, args, token)
                TOOL_CREATE_PAGE -> createPage(toolCallId, args, token)
                TOOL_APPEND_BLOCKS -> appendBlocks(toolCallId, args, token)
                TOOL_LIST_DATABASES -> listDatabases(toolCallId, token)
                else -> NotionToolResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            NotionToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private suspend fun search(id: String, args: com.google.gson.JsonObject, token: String): NotionToolResult {
        val query = args.get("query")?.asString ?: return NotionToolResult(id, false, "Parametro 'query' requerido")
        val filter = args.get("filter")?.asString

        val body = buildString {
            append("{\"query\":\"$query\"")
            if (filter != null && filter in listOf("page", "database")) {
                append(",\"filter\":{\"value\":\"$filter\",\"property\":\"object\"}")
            }
            append(",\"page_size\":10}")
        }

        val json = post("$API_URL/search", body, token) ?: return NotionToolResult(id, false, "Error al buscar en Notion")
        val results = json.getAsJsonArray("results")
        if (results == null || results.size() == 0) return NotionToolResult(id, true, "No se encontraron resultados para: \"$query\"")

        val formatted = buildString {
            appendLine("Resultados en Notion para: \"$query\"")
            appendLine()
            results.forEachIndexed { i, item ->
                val r = item.asJsonObject
                val objType = r.get("object")?.asString ?: ""
                val pageId = r.get("id")?.asString ?: ""
                val title = extractTitle(r)
                val icon = if (objType == "database") "DB" else "Pg"
                val lastEdited = r.get("last_edited_time")?.asString?.take(10) ?: ""
                appendLine("${i + 1}. [$icon] **$title**")
                appendLine("   ID: $pageId")
                appendLine("   Editado: $lastEdited")
                appendLine()
            }
        }
        return NotionToolResult(id, true, formatted.trim())
    }

    private suspend fun readPage(id: String, args: com.google.gson.JsonObject, token: String): NotionToolResult {
        val pageId = normalizeId(args.get("page_id")?.asString ?: return NotionToolResult(id, false, "Parametro 'page_id' requerido"))

        // Get page properties
        val page = get("$API_URL/pages/$pageId", token) ?: return NotionToolResult(id, false, "Pagina no encontrada: $pageId")
        val title = extractTitle(page)

        // Get page blocks (content)
        val blocksJson = get("$API_URL/blocks/$pageId/children?page_size=100", token)
        val blocks = blocksJson?.getAsJsonArray("results")

        val formatted = buildString {
            appendLine("# $title")
            appendLine("ID: $pageId")
            appendLine("---")
            appendLine()
            if (blocks != null) {
                blocks.forEach { block ->
                    val b = block.asJsonObject
                    val type = b.get("type")?.asString ?: return@forEach
                    val text = extractBlockText(b, type)
                    if (text.isNotBlank()) appendLine(text)
                }
            }
        }
        return NotionToolResult(id, true, formatted.trim())
    }

    private suspend fun createPage(id: String, args: com.google.gson.JsonObject, token: String): NotionToolResult {
        val parentId = normalizeId(args.get("parent_id")?.asString ?: return NotionToolResult(id, false, "Parametro 'parent_id' requerido"))
        val parentType = args.get("parent_type")?.asString ?: "page"
        val title = args.get("title")?.asString ?: return NotionToolResult(id, false, "Parametro 'title' requerido")
        val content = args.get("content")?.asString

        val parentKey = if (parentType == "database") "database_id" else "page_id"
        val blocksJson = if (!content.isNullOrBlank()) textToBlocks(content) else "[]"

        val body = """
        {
            "parent": {"$parentKey": "$parentId"},
            "properties": {
                "title": {"title": [{"text": {"content": ${com.google.gson.JsonPrimitive(title)}}}]}
            },
            "children": $blocksJson
        }
        """.trimIndent()

        val result = post("$API_URL/pages", body, token) ?: return NotionToolResult(id, false, "Error al crear pagina")
        val newId = result.get("id")?.asString
        val url = result.get("url")?.asString
        return NotionToolResult(id, true, "Pagina creada: \"$title\"\nID: $newId\nURL: $url")
    }

    private suspend fun appendBlocks(id: String, args: com.google.gson.JsonObject, token: String): NotionToolResult {
        val pageId = normalizeId(args.get("page_id")?.asString ?: return NotionToolResult(id, false, "Parametro 'page_id' requerido"))
        val content = args.get("content")?.asString ?: return NotionToolResult(id, false, "Parametro 'content' requerido")

        val blocksJson = textToBlocks(content)
        val body = """{"children": $blocksJson}"""

        val result = patch("$API_URL/blocks/$pageId/children", body, token)
            ?: return NotionToolResult(id, false, "Error al agregar contenido")

        return NotionToolResult(id, true, "Contenido agregado exitosamente a la pagina $pageId")
    }

    private suspend fun listDatabases(id: String, token: String): NotionToolResult {
        val body = """{"filter":{"value":"database","property":"object"},"page_size":20}"""
        val json = post("$API_URL/search", body, token) ?: return NotionToolResult(id, false, "Error al listar bases de datos")
        val results = json.getAsJsonArray("results")
        if (results == null || results.size() == 0) return NotionToolResult(id, true, "No se encontraron bases de datos accesibles")

        val formatted = buildString {
            appendLine("Bases de datos en Notion:")
            appendLine()
            results.forEachIndexed { i, item ->
                val r = item.asJsonObject
                val dbId = r.get("id")?.asString ?: ""
                val title = extractTitle(r)
                appendLine("${i + 1}. **$title**")
                appendLine("   ID: $dbId")
                appendLine()
            }
        }
        return NotionToolResult(id, true, formatted.trim())
    }

    // Convert plain text with markdown-like formatting to Notion blocks JSON
    private fun textToBlocks(text: String): String {
        val blocks = mutableListOf<String>()
        text.split("\n").forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> blocks.add(headingBlock(3, trimmed.removePrefix("### ")))
                trimmed.startsWith("## ") -> blocks.add(headingBlock(2, trimmed.removePrefix("## ")))
                trimmed.startsWith("# ") -> blocks.add(headingBlock(1, trimmed.removePrefix("# ")))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> blocks.add(bulletBlock(trimmed.drop(2)))
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> blocks.add(numberedBlock(trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), "")))
                trimmed.startsWith("> ") -> blocks.add(quoteBlock(trimmed.removePrefix("> ")))
                trimmed.isBlank() -> {} // skip empty lines
                else -> blocks.add(paragraphBlock(trimmed))
            }
        }
        return "[${blocks.joinToString(",")}]"
    }

    private fun paragraphBlock(text: String) = """{"object":"block","type":"paragraph","paragraph":{"rich_text":[{"type":"text","text":{"content":${com.google.gson.JsonPrimitive(text)}}}]}}"""
    private fun headingBlock(level: Int, text: String): String {
        val type = "heading_$level"
        return """{"object":"block","type":"$type","$type":{"rich_text":[{"type":"text","text":{"content":${com.google.gson.JsonPrimitive(text)}}}]}}"""
    }
    private fun bulletBlock(text: String) = """{"object":"block","type":"bulleted_list_item","bulleted_list_item":{"rich_text":[{"type":"text","text":{"content":${com.google.gson.JsonPrimitive(text)}}}]}}"""
    private fun numberedBlock(text: String) = """{"object":"block","type":"numbered_list_item","numbered_list_item":{"rich_text":[{"type":"text","text":{"content":${com.google.gson.JsonPrimitive(text)}}}]}}"""
    private fun quoteBlock(text: String) = """{"object":"block","type":"quote","quote":{"rich_text":[{"type":"text","text":{"content":${com.google.gson.JsonPrimitive(text)}}}]}}"""

    private fun extractTitle(obj: com.google.gson.JsonObject): String {
        val props = obj.getAsJsonObject("properties") ?: return "(sin titulo)"
        // Try "title" property first (pages), then "Name" (databases)
        for (key in listOf("title", "Name", "name")) {
            val prop = props.getAsJsonObject(key) ?: continue
            val titleArray = prop.getAsJsonArray("title") ?: continue
            if (titleArray.size() > 0) {
                return titleArray.joinToString("") { it.asJsonObject.get("plain_text")?.asString ?: "" }
            }
        }
        // Fallback: iterate all properties looking for title type
        for (entry in props.entrySet()) {
            val prop = entry.value?.asJsonObject ?: continue
            if (prop.get("type")?.asString == "title") {
                val arr = prop.getAsJsonArray("title") ?: continue
                return arr.joinToString("") { it.asJsonObject.get("plain_text")?.asString ?: "" }
            }
        }
        return "(sin titulo)"
    }

    private fun extractBlockText(block: com.google.gson.JsonObject, type: String): String {
        val content = block.getAsJsonObject(type) ?: return ""
        val richText = content.getAsJsonArray("rich_text") ?: return ""
        val text = richText.joinToString("") { it.asJsonObject.get("plain_text")?.asString ?: "" }
        return when (type) {
            "heading_1" -> "# $text"
            "heading_2" -> "## $text"
            "heading_3" -> "### $text"
            "bulleted_list_item" -> "- $text"
            "numbered_list_item" -> "1. $text"
            "quote" -> "> $text"
            "code" -> "```\n$text\n```"
            "to_do" -> {
                val checked = content.get("checked")?.asBoolean ?: false
                "${if (checked) "[x]" else "[ ]"} $text"
            }
            "divider" -> "---"
            else -> text
        }
    }

    private fun normalizeId(id: String): String = id.replace("-", "").let { raw ->
        if (raw.length == 32) "${raw.substring(0,8)}-${raw.substring(8,12)}-${raw.substring(12,16)}-${raw.substring(16,20)}-${raw.substring(20)}"
        else id
    }

    // HTTP helpers
    private suspend fun get(url: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Notion-Version", NOTION_VERSION)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET $url -> ${resp.code}: ${resp.body?.string()}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "GET error", e); null }
    }

    private suspend fun post(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Notion-Version", NOTION_VERSION)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "POST $url -> ${resp.code}: ${resp.body?.string()}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "POST error", e); null }
    }

    private suspend fun patch(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Notion-Version", NOTION_VERSION)
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "PATCH $url -> ${resp.code}: ${resp.body?.string()}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "PATCH error", e); null }
    }
}
