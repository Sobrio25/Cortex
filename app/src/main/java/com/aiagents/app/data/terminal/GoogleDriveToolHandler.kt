package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleDriveToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class GoogleDriveToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GoogleDriveToolHandler"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DOCS_API = "https://docs.googleapis.com/v1"
        const val TOOL_LIST_FILES = "gdrive_list_files"
        const val TOOL_READ_DOC = "gdrive_read_doc"
        const val TOOL_CREATE_DOC = "gdrive_create_doc"
        const val TOOL_SEARCH_FILES = "gdrive_search_files"

        val ALL_TOOL_NAMES = setOf(
            TOOL_LIST_FILES, TOOL_READ_DOC, TOOL_CREATE_DOC, TOOL_SEARCH_FILES
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST_FILES,
                "description" to "Lista archivos recientes en Google Drive del usuario.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "max_results" to mapOf("type" to "integer", "description" to "Cantidad maxima de archivos (default 10, max 30)"),
                        "type" to mapOf("type" to "string", "description" to "Filtrar por tipo: 'document', 'spreadsheet', 'presentation', 'folder' (opcional)")
                    ),
                    "required" to emptyList<String>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SEARCH_FILES,
                "description" to "Busca archivos en Google Drive por nombre o contenido.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Texto a buscar en nombres y contenido de archivos")
                    ),
                    "required" to listOf("query"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_READ_DOC,
                "description" to "Lee el contenido de un Google Doc por su ID. Devuelve el texto completo del documento.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "file_id" to mapOf("type" to "string", "description" to "ID del archivo de Google Drive")
                    ),
                    "required" to listOf("file_id"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_CREATE_DOC,
                "description" to "Crea un nuevo Google Doc con titulo y contenido.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "title" to mapOf("type" to "string", "description" to "Titulo del documento"),
                        "content" to mapOf("type" to "string", "description" to "Contenido del documento en texto plano")
                    ),
                    "required" to listOf("title"))
            ))
        )
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String, accessToken: String): GoogleDriveToolResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_LIST_FILES -> listFiles(toolCallId, args, accessToken)
                TOOL_SEARCH_FILES -> searchFiles(toolCallId, args, accessToken)
                TOOL_READ_DOC -> readDoc(toolCallId, args, accessToken)
                TOOL_CREATE_DOC -> createDoc(toolCallId, args, accessToken)
                else -> GoogleDriveToolResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            GoogleDriveToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private suspend fun listFiles(id: String, args: com.google.gson.JsonObject, token: String): GoogleDriveToolResult {
        val maxResults = args.get("max_results")?.asInt?.coerceIn(1, 30) ?: 10
        val type = args.get("type")?.asString

        val mimeFilter = when (type) {
            "document" -> " and mimeType='application/vnd.google-apps.document'"
            "spreadsheet" -> " and mimeType='application/vnd.google-apps.spreadsheet'"
            "presentation" -> " and mimeType='application/vnd.google-apps.presentation'"
            "folder" -> " and mimeType='application/vnd.google-apps.folder'"
            else -> ""
        }

        val query = "trashed=false$mimeFilter"
        val url = "$DRIVE_API/files?q=${enc(query)}&pageSize=$maxResults&fields=files(id,name,mimeType,modifiedTime,size,webViewLink)&orderBy=modifiedTime desc"
        val json = get(url, token) ?: return GoogleDriveToolResult(id, false, "Error al listar archivos")

        val files = json.getAsJsonArray("files")
        if (files == null || files.size() == 0) return GoogleDriveToolResult(id, true, "No se encontraron archivos")

        val formatted = buildString {
            appendLine("Archivos en Google Drive:")
            appendLine()
            files.forEachIndexed { i, item ->
                val f = item.asJsonObject
                val name = f.get("name")?.asString ?: "(sin nombre)"
                val fileId = f.get("id")?.asString ?: ""
                val mime = f.get("mimeType")?.asString ?: ""
                val modified = f.get("modifiedTime")?.asString?.take(10) ?: ""
                val link = f.get("webViewLink")?.asString ?: ""
                val typeIcon = when {
                    mime.contains("document") -> "Doc"
                    mime.contains("spreadsheet") -> "Sheet"
                    mime.contains("presentation") -> "Slides"
                    mime.contains("folder") -> "Folder"
                    else -> "File"
                }
                appendLine("${i + 1}. [$typeIcon] **$name**")
                appendLine("   ID: $fileId | Modificado: $modified")
                if (link.isNotBlank()) appendLine("   URL: $link")
                appendLine()
            }
        }
        return GoogleDriveToolResult(id, true, formatted.trim())
    }

    private suspend fun searchFiles(id: String, args: com.google.gson.JsonObject, token: String): GoogleDriveToolResult {
        val query = args.get("query")?.asString ?: return GoogleDriveToolResult(id, false, "Parametro 'query' requerido")
        val driveQuery = "fullText contains '${query.replace("'", "\\'")}' and trashed=false"
        val url = "$DRIVE_API/files?q=${enc(driveQuery)}&pageSize=15&fields=files(id,name,mimeType,modifiedTime,webViewLink)"
        val json = get(url, token) ?: return GoogleDriveToolResult(id, false, "Error al buscar archivos")

        val files = json.getAsJsonArray("files")
        if (files == null || files.size() == 0) return GoogleDriveToolResult(id, true, "No se encontraron archivos para: \"$query\"")

        val formatted = buildString {
            appendLine("Archivos encontrados para: \"$query\"")
            appendLine()
            files.forEachIndexed { i, item ->
                val f = item.asJsonObject
                appendLine("${i + 1}. **${f.get("name")?.asString}**")
                appendLine("   ID: ${f.get("id")?.asString} | ${f.get("modifiedTime")?.asString?.take(10)}")
                val link = f.get("webViewLink")?.asString
                if (!link.isNullOrBlank()) appendLine("   URL: $link")
                appendLine()
            }
        }
        return GoogleDriveToolResult(id, true, formatted.trim())
    }

    private suspend fun readDoc(id: String, args: com.google.gson.JsonObject, token: String): GoogleDriveToolResult {
        val fileId = args.get("file_id")?.asString ?: return GoogleDriveToolResult(id, false, "Parametro 'file_id' requerido")

        // Export as plain text
        val url = "$DRIVE_API/files/$fileId/export?mimeType=text/plain"
        val text = getText(url, token) ?: return GoogleDriveToolResult(id, false, "Error al leer documento. Verifica que sea un Google Doc y que tengas acceso.")

        return GoogleDriveToolResult(id, true, buildString {
            appendLine("Contenido del documento (ID: $fileId):")
            appendLine("---")
            if (text.length > 20000) {
                append(text.take(20000))
                appendLine("\n\n... (truncado, documento muy largo)")
            } else {
                append(text)
            }
        })
    }

    private suspend fun createDoc(id: String, args: com.google.gson.JsonObject, token: String): GoogleDriveToolResult {
        val title = args.get("title")?.asString ?: return GoogleDriveToolResult(id, false, "Parametro 'title' requerido")
        val content = args.get("content")?.asString

        // Step 1: Create empty doc via Docs API
        val createBody = """{"title":${com.google.gson.JsonPrimitive(title)}}"""
        val docResult = post("$DOCS_API/documents", createBody, token)
            ?: return GoogleDriveToolResult(id, false, "Error al crear documento")

        val docId = docResult.get("documentId")?.asString ?: return GoogleDriveToolResult(id, false, "No se obtuvo ID del documento")

        // Step 2: Insert content if provided
        if (!content.isNullOrBlank()) {
            val updateBody = """
            {
                "requests": [{
                    "insertText": {
                        "location": {"index": 1},
                        "text": ${com.google.gson.JsonPrimitive(content)}
                    }
                }]
            }
            """.trimIndent()
            post("$DOCS_API/documents/$docId:batchUpdate", updateBody, token)
        }

        val docUrl = "https://docs.google.com/document/d/$docId/edit"
        return GoogleDriveToolResult(id, true, "Documento creado: \"$title\"\nID: $docId\nURL: $docUrl")
    }

    // HTTP helpers
    private suspend fun get(url: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "GET error", e); null }
    }

    private suspend fun getText(url: String, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET text ${resp.code}"); return@withContext null }
            resp.body?.string()
        } catch (e: Exception) { Log.e(TAG, "GET text error", e); null }
    }

    private suspend fun post(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "POST ${resp.code}: ${resp.body?.string()}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "POST error", e); null }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
