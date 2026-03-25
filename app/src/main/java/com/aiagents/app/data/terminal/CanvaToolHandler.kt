package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class CanvaToolResult(
    val toolCallId: String,
    val content: String
)

@Singleton
class CanvaToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "CanvaToolHandler"
        private const val API_URL = "https://api.canva.com/rest/v1"

        const val TOOL_CREATE_DESIGN = "canva_create_design"
        const val TOOL_GET_DESIGN = "canva_get_design"
        const val TOOL_LIST_DESIGNS = "canva_list_designs"
        const val TOOL_EXPORT_DESIGN = "canva_export_design"
        const val TOOL_UPLOAD_ASSET = "canva_upload_asset"
        const val TOOL_LIST_BRAND_TEMPLATES = "canva_list_brand_templates"
        const val TOOL_GET_BRAND_TEMPLATE = "canva_get_brand_template"
        const val TOOL_AUTOFILL_TEMPLATE = "canva_autofill_template"
        const val TOOL_CREATE_FOLDER = "canva_create_folder"
        const val TOOL_GET_FOLDER = "canva_get_folder"
        const val TOOL_LIST_FOLDER_ITEMS = "canva_list_folder_items"
        const val TOOL_MOVE_TO_FOLDER = "canva_move_to_folder"
        const val TOOL_DELETE_FOLDER = "canva_delete_folder"
        const val TOOL_ADD_COMMENT = "canva_add_comment"
        const val TOOL_LIST_COMMENTS = "canva_list_comments"
        const val TOOL_REPLY_COMMENT = "canva_reply_comment"
        const val TOOL_GET_USER_PROFILE = "canva_get_user_profile"
        const val TOOL_IMPORT_DESIGN = "canva_import_design"

        val ALL_TOOL_NAMES = setOf(
            TOOL_CREATE_DESIGN, TOOL_GET_DESIGN, TOOL_LIST_DESIGNS,
            TOOL_EXPORT_DESIGN, TOOL_UPLOAD_ASSET,
            TOOL_LIST_BRAND_TEMPLATES, TOOL_GET_BRAND_TEMPLATE, TOOL_AUTOFILL_TEMPLATE,
            TOOL_CREATE_FOLDER, TOOL_GET_FOLDER, TOOL_LIST_FOLDER_ITEMS,
            TOOL_MOVE_TO_FOLDER, TOOL_DELETE_FOLDER,
            TOOL_ADD_COMMENT, TOOL_LIST_COMMENTS, TOOL_REPLY_COMMENT,
            TOOL_GET_USER_PROFILE, TOOL_IMPORT_DESIGN
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            // ── Diseños ──
            toolDef(TOOL_CREATE_DESIGN,
                "Crea un nuevo diseño en Canva con el título y tipo especificado.",
                mapOf(
                    "title" to param("string", "Título del diseño"),
                    "design_type" to param("string", "Tipo de diseño: Poster, Presentation, InstagramPost, etc. (default: Poster)")
                ),
                listOf("title")
            ),
            toolDef(TOOL_GET_DESIGN,
                "Obtiene los detalles de un diseño específico de Canva.",
                mapOf(
                    "design_id" to param("string", "ID del diseño en Canva")
                ),
                listOf("design_id")
            ),
            toolDef(TOOL_LIST_DESIGNS,
                "Lista los diseños del usuario en Canva. Puede filtrar por consulta.",
                mapOf(
                    "query" to param("string", "Consulta de búsqueda para filtrar diseños (opcional)"),
                    "continuation" to param("string", "Token de paginación para obtener más resultados (opcional)")
                ),
                emptyList()
            ),
            toolDef(TOOL_EXPORT_DESIGN,
                "Exporta un diseño de Canva a un formato específico (png, pdf, jpg). Devuelve la URL de descarga.",
                mapOf(
                    "design_id" to param("string", "ID del diseño a exportar"),
                    "format" to param("string", "Formato de exportación: 'png', 'pdf' o 'jpg' (default: png)")
                ),
                listOf("design_id")
            ),
            // ── Assets ──
            toolDef(TOOL_UPLOAD_ASSET,
                "Sube un asset (imagen) a Canva desde una URL.",
                mapOf(
                    "url" to param("string", "URL de la imagen a subir"),
                    "name" to param("string", "Nombre del asset (opcional)")
                ),
                listOf("url")
            ),
            // ── Brand Templates ──
            toolDef(TOOL_LIST_BRAND_TEMPLATES,
                "Lista las plantillas de marca disponibles en Canva.",
                mapOf(
                    "query" to param("string", "Consulta de búsqueda para filtrar plantillas (opcional)"),
                    "continuation" to param("string", "Token de paginación (opcional)")
                ),
                emptyList()
            ),
            toolDef(TOOL_GET_BRAND_TEMPLATE,
                "Obtiene los detalles de una plantilla de marca específica de Canva.",
                mapOf(
                    "brand_template_id" to param("string", "ID de la plantilla de marca")
                ),
                listOf("brand_template_id")
            ),
            toolDef(TOOL_AUTOFILL_TEMPLATE,
                "Rellena automáticamente una plantilla de marca con datos proporcionados. Devuelve la URL del diseño generado.",
                mapOf(
                    "brand_template_id" to param("string", "ID de la plantilla de marca"),
                    "data" to param("string", "Objeto JSON mapeando nombres de campos a valores. Ej: '{\"title\":\"Mi Título\",\"subtitle\":\"Subtítulo\"}'")
                ),
                listOf("brand_template_id", "data")
            ),
            // ── Carpetas ──
            toolDef(TOOL_CREATE_FOLDER,
                "Crea una nueva carpeta en Canva.",
                mapOf(
                    "name" to param("string", "Nombre de la carpeta"),
                    "parent_folder_id" to param("string", "ID de la carpeta padre (opcional, se crea en la raíz si se omite)")
                ),
                listOf("name")
            ),
            toolDef(TOOL_GET_FOLDER,
                "Obtiene los detalles de una carpeta específica de Canva.",
                mapOf(
                    "folder_id" to param("string", "ID de la carpeta")
                ),
                listOf("folder_id")
            ),
            toolDef(TOOL_LIST_FOLDER_ITEMS,
                "Lista los elementos dentro de una carpeta de Canva.",
                mapOf(
                    "folder_id" to param("string", "ID de la carpeta"),
                    "continuation" to param("string", "Token de paginación (opcional)")
                ),
                listOf("folder_id")
            ),
            toolDef(TOOL_MOVE_TO_FOLDER,
                "Mueve un elemento (diseño) a una carpeta de Canva.",
                mapOf(
                    "folder_id" to param("string", "ID de la carpeta destino"),
                    "item_id" to param("string", "ID del elemento a mover"),
                    "item_type" to param("string", "Tipo de elemento: 'design', 'folder', etc. (default: design)")
                ),
                listOf("folder_id", "item_id")
            ),
            toolDef(TOOL_DELETE_FOLDER,
                "Elimina una carpeta de Canva.",
                mapOf(
                    "folder_id" to param("string", "ID de la carpeta a eliminar")
                ),
                listOf("folder_id")
            ),
            // ── Comentarios ──
            toolDef(TOOL_ADD_COMMENT,
                "Agrega un comentario a un diseño de Canva.",
                mapOf(
                    "design_id" to param("string", "ID del diseño"),
                    "message" to param("string", "Texto del comentario")
                ),
                listOf("design_id", "message")
            ),
            toolDef(TOOL_LIST_COMMENTS,
                "Lista los comentarios de un diseño de Canva.",
                mapOf(
                    "design_id" to param("string", "ID del diseño")
                ),
                listOf("design_id")
            ),
            toolDef(TOOL_REPLY_COMMENT,
                "Responde a un comentario existente en un diseño de Canva.",
                mapOf(
                    "comment_id" to param("string", "ID del comentario al que responder"),
                    "message" to param("string", "Texto de la respuesta")
                ),
                listOf("comment_id", "message")
            ),
            // ── Usuario ──
            toolDef(TOOL_GET_USER_PROFILE,
                "Obtiene el perfil del usuario autenticado en Canva.",
                emptyMap(),
                emptyList()
            ),
            // ── Importar ──
            toolDef(TOOL_IMPORT_DESIGN,
                "Importa un diseño a Canva desde una URL de archivo externo.",
                mapOf(
                    "url" to param("string", "URL del archivo a importar"),
                    "title" to param("string", "Título del diseño importado (opcional)")
                ),
                listOf("url")
            )
        )

        private fun param(type: String, desc: String) = mapOf("type" to type, "description" to desc)

        private fun toolDef(name: String, desc: String, props: Map<String, Map<String, String>>, required: List<String>) = mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to name,
                "description" to desc,
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to props,
                    "required" to required
                )
            )
        )
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String, apiKey: String): CanvaToolResult {
        if (apiKey.isBlank()) {
            return CanvaToolResult(toolCallId,
                "Error: Canva no está configurado. Proporciona tu API key de Canva Connect.")
        }

        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_CREATE_DESIGN -> createDesign(toolCallId, args, apiKey)
                TOOL_GET_DESIGN -> getDesign(toolCallId, args, apiKey)
                TOOL_LIST_DESIGNS -> listDesigns(toolCallId, args, apiKey)
                TOOL_EXPORT_DESIGN -> exportDesign(toolCallId, args, apiKey)
                TOOL_UPLOAD_ASSET -> uploadAsset(toolCallId, args, apiKey)
                TOOL_LIST_BRAND_TEMPLATES -> listBrandTemplates(toolCallId, args, apiKey)
                TOOL_GET_BRAND_TEMPLATE -> getBrandTemplate(toolCallId, args, apiKey)
                TOOL_AUTOFILL_TEMPLATE -> autofillTemplate(toolCallId, args, apiKey)
                TOOL_CREATE_FOLDER -> createFolder(toolCallId, args, apiKey)
                TOOL_GET_FOLDER -> getFolder(toolCallId, args, apiKey)
                TOOL_LIST_FOLDER_ITEMS -> listFolderItems(toolCallId, args, apiKey)
                TOOL_MOVE_TO_FOLDER -> moveToFolder(toolCallId, args, apiKey)
                TOOL_DELETE_FOLDER -> deleteFolder(toolCallId, args, apiKey)
                TOOL_ADD_COMMENT -> addComment(toolCallId, args, apiKey)
                TOOL_LIST_COMMENTS -> listComments(toolCallId, args, apiKey)
                TOOL_REPLY_COMMENT -> replyComment(toolCallId, args, apiKey)
                TOOL_GET_USER_PROFILE -> getUserProfile(toolCallId, args, apiKey)
                TOOL_IMPORT_DESIGN -> importDesign(toolCallId, args, apiKey)
                else -> CanvaToolResult(toolCallId, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            CanvaToolResult(toolCallId, "Error: ${e.message}")
        }
    }

    // ── Diseños ──────────────────────────────────────────────────────────────

    private suspend fun createDesign(id: String, args: JsonObject, token: String): CanvaToolResult {
        val title = args.get("title")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'title' requerido")
        val designType = args.get("design_type")?.asString ?: "Poster"

        val jsonBody = buildString {
            append("{\"asset_type\":\"design\"")
            append(",\"title\":${JsonPrimitive(title)}")
            append(",\"design_type\":${JsonPrimitive(designType)}")
            append("}")
        }

        val result = post("$API_URL/designs", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al crear el diseño en Canva")

        val designId = result.get("design")?.asJsonObject?.get("id")?.asString
            ?: result.get("id")?.asString ?: "desconocido"
        val editUrl = result.get("design")?.asJsonObject?.get("urls")?.asJsonObject?.get("edit_url")?.asString
            ?: result.get("urls")?.asJsonObject?.get("edit_url")?.asString ?: ""

        return CanvaToolResult(id, buildString {
            appendLine("Diseño creado exitosamente")
            appendLine("ID: $designId")
            appendLine("Título: $title")
            appendLine("Tipo: $designType")
            if (editUrl.isNotBlank()) appendLine("URL de edición: $editUrl")
        }.trim())
    }

    private suspend fun getDesign(id: String, args: JsonObject, token: String): CanvaToolResult {
        val designId = args.get("design_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'design_id' requerido")

        val json = get("$API_URL/designs/$designId", token)
            ?: return CanvaToolResult(id, "Diseño no encontrado: $designId")

        val design = json.getAsJsonObject("design") ?: json

        return CanvaToolResult(id, buildString {
            appendLine("**Diseño: ${design.get("title")?.asString ?: "Sin título"}**")
            appendLine("ID: ${design.get("id")?.asString ?: designId}")
            val createdAt = design.get("created_at")?.asString
            if (createdAt != null) appendLine("Creado: $createdAt")
            val updatedAt = design.get("updated_at")?.asString
            if (updatedAt != null) appendLine("Actualizado: $updatedAt")
            val thumbnail = design.get("thumbnail")?.asJsonObject?.get("url")?.asString
            if (!thumbnail.isNullOrBlank()) appendLine("Miniatura: $thumbnail")
            val editUrl = design.get("urls")?.asJsonObject?.get("edit_url")?.asString
            if (!editUrl.isNullOrBlank()) appendLine("URL de edición: $editUrl")
            val viewUrl = design.get("urls")?.asJsonObject?.get("view_url")?.asString
            if (!viewUrl.isNullOrBlank()) appendLine("URL de vista: $viewUrl")
        }.trim())
    }

    private suspend fun listDesigns(id: String, args: JsonObject, token: String): CanvaToolResult {
        var url = "$API_URL/designs"
        val params = mutableListOf<String>()
        val query = args.get("query")?.asString
        if (!query.isNullOrBlank()) params.add("query=${enc(query)}")
        val continuation = args.get("continuation")?.asString
        if (!continuation.isNullOrBlank()) params.add("continuation=${enc(continuation)}")
        if (params.isNotEmpty()) url += "?" + params.joinToString("&")

        val json = get(url, token)
            ?: return CanvaToolResult(id, "Error al listar diseños")

        val items = json.getAsJsonArray("items")
        if (items == null || items.size() == 0) return CanvaToolResult(id, "No se encontraron diseños")

        val cont = json.get("continuation")?.asString

        return CanvaToolResult(id, buildString {
            appendLine("Diseños encontrados (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val d = item.asJsonObject
                appendLine("${i + 1}. **${d.get("title")?.asString ?: "Sin título"}**")
                appendLine("   ID: ${d.get("id")?.asString}")
                val createdAt = d.get("created_at")?.asString
                if (createdAt != null) appendLine("   Creado: $createdAt")
                val thumbnail = d.get("thumbnail")?.asJsonObject?.get("url")?.asString
                if (!thumbnail.isNullOrBlank()) appendLine("   Miniatura: $thumbnail")
                appendLine()
            }
            if (!cont.isNullOrBlank()) appendLine("Más resultados disponibles. Usa continuation: \"$cont\"")
        }.trim())
    }

    private suspend fun exportDesign(id: String, args: JsonObject, token: String): CanvaToolResult {
        val designId = args.get("design_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'design_id' requerido")
        val format = args.get("format")?.asString ?: "png"

        val jsonBody = buildString {
            append("{\"design_id\":${JsonPrimitive(designId)}")
            append(",\"format\":${JsonPrimitive(format)}")
            append("}")
        }

        val result = post("$API_URL/exports", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al iniciar la exportación del diseño")

        val exportId = result.get("job")?.asJsonObject?.get("id")?.asString
            ?: result.get("id")?.asString
            ?: return CanvaToolResult(id, "No se pudo obtener el ID del trabajo de exportación")

        // Polling: esperar hasta que la exportación termine
        val maxAttempts = 15 // 15 * 2s = 30s max
        for (attempt in 1..maxAttempts) {
            delay(2000)

            val status = get("$API_URL/exports/$exportId", token)
                ?: continue

            val job = status.getAsJsonObject("job") ?: status
            val jobStatus = job.get("status")?.asString ?: ""

            when (jobStatus) {
                "completed", "success" -> {
                    val urls = job.getAsJsonArray("urls")
                    val downloadUrl = if (urls != null && urls.size() > 0) {
                        urls[0].asString
                    } else {
                        job.get("url")?.asString ?: ""
                    }

                    return CanvaToolResult(id, buildString {
                        appendLine("Exportación completada")
                        appendLine("Formato: $format")
                        appendLine("ID de exportación: $exportId")
                        if (downloadUrl.isNotBlank()) appendLine("URL de descarga: $downloadUrl")
                    }.trim())
                }
                "failed" -> {
                    val error = job.get("error")?.asJsonObject?.get("message")?.asString ?: "Error desconocido"
                    return CanvaToolResult(id, "Error en la exportación: $error")
                }
            }
        }

        return CanvaToolResult(id, "Timeout: La exportación no se completó en 30 segundos. ID del trabajo: $exportId")
    }

    // ── Assets ───────────────────────────────────────────────────────────────

    private suspend fun uploadAsset(id: String, args: JsonObject, token: String): CanvaToolResult {
        val url = args.get("url")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'url' requerido")
        val name = args.get("name")?.asString

        val jsonBody = buildString {
            append("{\"url\":${JsonPrimitive(url)}")
            if (!name.isNullOrBlank()) append(",\"name\":${JsonPrimitive(name)}")
            append("}")
        }

        val result = post("$API_URL/asset-uploads", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al subir el asset a Canva")

        val job = result.getAsJsonObject("job") ?: result
        val assetId = job.get("asset_id")?.asString
            ?: job.get("id")?.asString ?: "desconocido"

        return CanvaToolResult(id, buildString {
            appendLine("Asset subido exitosamente")
            appendLine("ID: $assetId")
            if (!name.isNullOrBlank()) appendLine("Nombre: $name")
            appendLine("URL origen: $url")
        }.trim())
    }

    // ── Brand Templates ──────────────────────────────────────────────────────

    private suspend fun listBrandTemplates(id: String, args: JsonObject, token: String): CanvaToolResult {
        var url = "$API_URL/brand-templates"
        val params = mutableListOf<String>()
        val query = args.get("query")?.asString
        if (!query.isNullOrBlank()) params.add("query=${enc(query)}")
        val continuation = args.get("continuation")?.asString
        if (!continuation.isNullOrBlank()) params.add("continuation=${enc(continuation)}")
        if (params.isNotEmpty()) url += "?" + params.joinToString("&")

        val json = get(url, token)
            ?: return CanvaToolResult(id, "Error al listar plantillas de marca")

        val items = json.getAsJsonArray("items")
        if (items == null || items.size() == 0) return CanvaToolResult(id, "No se encontraron plantillas de marca")

        val cont = json.get("continuation")?.asString

        return CanvaToolResult(id, buildString {
            appendLine("Plantillas de marca (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val t = item.asJsonObject
                appendLine("${i + 1}. **${t.get("title")?.asString ?: "Sin título"}**")
                appendLine("   ID: ${t.get("id")?.asString}")
                val createdAt = t.get("created_at")?.asString
                if (createdAt != null) appendLine("   Creado: $createdAt")
                appendLine()
            }
            if (!cont.isNullOrBlank()) appendLine("Más resultados disponibles. Usa continuation: \"$cont\"")
        }.trim())
    }

    private suspend fun getBrandTemplate(id: String, args: JsonObject, token: String): CanvaToolResult {
        val templateId = args.get("brand_template_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'brand_template_id' requerido")

        val json = get("$API_URL/brand-templates/$templateId", token)
            ?: return CanvaToolResult(id, "Plantilla de marca no encontrada: $templateId")

        val template = json.getAsJsonObject("brand_template") ?: json

        return CanvaToolResult(id, buildString {
            appendLine("**Plantilla: ${template.get("title")?.asString ?: "Sin título"}**")
            appendLine("ID: ${template.get("id")?.asString ?: templateId}")
            val createdAt = template.get("created_at")?.asString
            if (createdAt != null) appendLine("Creado: $createdAt")
            val updatedAt = template.get("updated_at")?.asString
            if (updatedAt != null) appendLine("Actualizado: $updatedAt")
            val thumbnail = template.get("thumbnail")?.asJsonObject?.get("url")?.asString
            if (!thumbnail.isNullOrBlank()) appendLine("Miniatura: $thumbnail")
            // List dataset fields if available
            val dataset = template.getAsJsonObject("dataset")
            if (dataset != null && dataset.size() > 0) {
                appendLine()
                appendLine("Campos disponibles:")
                dataset.entrySet().forEach { (key, value) ->
                    val fieldType = value.asJsonObject?.get("type")?.asString ?: "text"
                    appendLine("  - $key ($fieldType)")
                }
            }
        }.trim())
    }

    private suspend fun autofillTemplate(id: String, args: JsonObject, token: String): CanvaToolResult {
        val templateId = args.get("brand_template_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'brand_template_id' requerido")
        val dataStr = args.get("data")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'data' requerido")

        // Parse the data string as JSON to build the data object
        val dataObj = try {
            JsonParser.parseString(dataStr).asJsonObject
        } catch (e: Exception) {
            return CanvaToolResult(id, "Error: El parámetro 'data' no es un JSON válido: ${e.message}")
        }

        // Build the data map with type wrappers
        val dataFields = buildString {
            append("{")
            val entries = dataObj.entrySet().toList()
            entries.forEachIndexed { i, (key, value) ->
                append("${JsonPrimitive(key)}:{\"type\":\"text\",\"text\":${JsonPrimitive(value.asString)}}")
                if (i < entries.size - 1) append(",")
            }
            append("}")
        }

        val jsonBody = buildString {
            append("{\"brand_template_id\":${JsonPrimitive(templateId)}")
            append(",\"data\":$dataFields")
            append("}")
        }

        val result = post("$API_URL/autofills", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al iniciar el autofill de la plantilla")

        val job = result.getAsJsonObject("job") ?: result
        val autofillId = job.get("id")?.asString
            ?: return CanvaToolResult(id, "No se pudo obtener el ID del trabajo de autofill")

        // Polling: esperar hasta que el autofill termine
        val maxAttempts = 15 // 15 * 2s = 30s max
        for (attempt in 1..maxAttempts) {
            delay(2000)

            val status = get("$API_URL/autofills/$autofillId", token)
                ?: continue

            val statusJob = status.getAsJsonObject("job") ?: status
            val jobStatus = statusJob.get("status")?.asString ?: ""

            when (jobStatus) {
                "completed", "success" -> {
                    val design = statusJob.getAsJsonObject("result")?.getAsJsonObject("design")
                        ?: statusJob.getAsJsonObject("design")
                    val designId = design?.get("id")?.asString ?: ""
                    val editUrl = design?.get("urls")?.asJsonObject?.get("edit_url")?.asString ?: ""

                    return CanvaToolResult(id, buildString {
                        appendLine("Autofill completado exitosamente")
                        if (designId.isNotBlank()) appendLine("ID del diseño: $designId")
                        if (editUrl.isNotBlank()) appendLine("URL de edición: $editUrl")
                    }.trim())
                }
                "failed" -> {
                    val error = statusJob.get("error")?.asJsonObject?.get("message")?.asString ?: "Error desconocido"
                    return CanvaToolResult(id, "Error en el autofill: $error")
                }
            }
        }

        return CanvaToolResult(id, "Timeout: El autofill no se completó en 30 segundos. ID del trabajo: $autofillId")
    }

    // ── Carpetas ─────────────────────────────────────────────────────────────

    private suspend fun createFolder(id: String, args: JsonObject, token: String): CanvaToolResult {
        val name = args.get("name")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'name' requerido")
        val parentFolderId = args.get("parent_folder_id")?.asString

        val jsonBody = buildString {
            append("{\"name\":${JsonPrimitive(name)}")
            if (!parentFolderId.isNullOrBlank()) append(",\"parent_folder_id\":${JsonPrimitive(parentFolderId)}")
            append("}")
        }

        val result = post("$API_URL/folders", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al crear la carpeta en Canva")

        val folder = result.getAsJsonObject("folder") ?: result
        val folderId = folder.get("id")?.asString ?: "desconocido"

        return CanvaToolResult(id, buildString {
            appendLine("Carpeta creada exitosamente")
            appendLine("ID: $folderId")
            appendLine("Nombre: $name")
            if (!parentFolderId.isNullOrBlank()) appendLine("Carpeta padre: $parentFolderId")
        }.trim())
    }

    private suspend fun getFolder(id: String, args: JsonObject, token: String): CanvaToolResult {
        val folderId = args.get("folder_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'folder_id' requerido")

        val json = get("$API_URL/folders/$folderId", token)
            ?: return CanvaToolResult(id, "Carpeta no encontrada: $folderId")

        val folder = json.getAsJsonObject("folder") ?: json

        return CanvaToolResult(id, buildString {
            appendLine("**Carpeta: ${folder.get("name")?.asString ?: "Sin nombre"}**")
            appendLine("ID: ${folder.get("id")?.asString ?: folderId}")
            val createdAt = folder.get("created_at")?.asString
            if (createdAt != null) appendLine("Creado: $createdAt")
            val updatedAt = folder.get("updated_at")?.asString
            if (updatedAt != null) appendLine("Actualizado: $updatedAt")
        }.trim())
    }

    private suspend fun listFolderItems(id: String, args: JsonObject, token: String): CanvaToolResult {
        val folderId = args.get("folder_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'folder_id' requerido")
        var url = "$API_URL/folders/$folderId/items"
        val continuation = args.get("continuation")?.asString
        if (!continuation.isNullOrBlank()) url += "?continuation=${enc(continuation)}"

        val json = get(url, token)
            ?: return CanvaToolResult(id, "Error al listar los elementos de la carpeta")

        val items = json.getAsJsonArray("items")
        if (items == null || items.size() == 0) return CanvaToolResult(id, "La carpeta está vacía")

        val cont = json.get("continuation")?.asString

        return CanvaToolResult(id, buildString {
            appendLine("Elementos en carpeta (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val it = item.asJsonObject
                val itemType = it.get("type")?.asString ?: "desconocido"
                val itemTitle = it.get("title")?.asString ?: it.get("name")?.asString ?: "Sin título"
                appendLine("${i + 1}. [$itemType] **$itemTitle**")
                appendLine("   ID: ${it.get("id")?.asString}")
                appendLine()
            }
            if (!cont.isNullOrBlank()) appendLine("Más resultados disponibles. Usa continuation: \"$cont\"")
        }.trim())
    }

    private suspend fun moveToFolder(id: String, args: JsonObject, token: String): CanvaToolResult {
        val folderId = args.get("folder_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'folder_id' requerido")
        val itemId = args.get("item_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'item_id' requerido")
        val itemType = args.get("item_type")?.asString ?: "design"

        val jsonBody = buildString {
            append("{\"item_id\":${JsonPrimitive(itemId)}")
            append(",\"item_type\":${JsonPrimitive(itemType)}")
            append("}")
        }

        val result = post("$API_URL/folders/$folderId/items", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al mover el elemento a la carpeta")

        return CanvaToolResult(id, "Elemento movido exitosamente a la carpeta $folderId (tipo: $itemType, ID: $itemId)")
    }

    private suspend fun deleteFolder(id: String, args: JsonObject, token: String): CanvaToolResult {
        val folderId = args.get("folder_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'folder_id' requerido")

        val success = deleteNoBody("$API_URL/folders/$folderId", token)
        return if (success in 200..299) {
            CanvaToolResult(id, "Carpeta eliminada exitosamente: $folderId")
        } else {
            CanvaToolResult(id, "Error al eliminar la carpeta: $folderId (código: $success)")
        }
    }

    // ── Comentarios ──────────────────────────────────────────────────────────

    private suspend fun addComment(id: String, args: JsonObject, token: String): CanvaToolResult {
        val designId = args.get("design_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'design_id' requerido")
        val message = args.get("message")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'message' requerido")

        val jsonBody = "{\"message\":${JsonPrimitive(message)}}"

        val result = post("$API_URL/designs/$designId/comments", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al agregar comentario al diseño")

        val comment = result.getAsJsonObject("comment") ?: result
        val commentId = comment.get("id")?.asString ?: "desconocido"

        return CanvaToolResult(id, buildString {
            appendLine("Comentario agregado exitosamente")
            appendLine("ID del comentario: $commentId")
            appendLine("Diseño: $designId")
            appendLine("Mensaje: $message")
        }.trim())
    }

    private suspend fun listComments(id: String, args: JsonObject, token: String): CanvaToolResult {
        val designId = args.get("design_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'design_id' requerido")

        val json = get("$API_URL/designs/$designId/comments", token)
            ?: return CanvaToolResult(id, "Error al listar comentarios del diseño")

        val items = json.getAsJsonArray("items")
            ?: json.getAsJsonArray("comments")
        if (items == null || items.size() == 0) return CanvaToolResult(id, "No hay comentarios en el diseño $designId")

        return CanvaToolResult(id, buildString {
            appendLine("Comentarios en diseño $designId (${items.size()}):")
            appendLine()
            items.forEachIndexed { i, item ->
                val c = item.asJsonObject
                val author = c.get("author")?.asJsonObject?.get("display_name")?.asString
                    ?: c.get("author")?.asJsonObject?.get("id")?.asString ?: "Desconocido"
                appendLine("${i + 1}. **$author**: ${c.get("message")?.asString ?: ""}")
                appendLine("   ID: ${c.get("id")?.asString}")
                val createdAt = c.get("created_at")?.asString
                if (createdAt != null) appendLine("   Fecha: $createdAt")
                val replies = c.getAsJsonArray("replies")
                if (replies != null && replies.size() > 0) {
                    appendLine("   Respuestas: ${replies.size()}")
                }
                appendLine()
            }
        }.trim())
    }

    private suspend fun replyComment(id: String, args: JsonObject, token: String): CanvaToolResult {
        val commentId = args.get("comment_id")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'comment_id' requerido")
        val message = args.get("message")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'message' requerido")

        val jsonBody = "{\"message\":${JsonPrimitive(message)}}"

        val result = post("$API_URL/comments/$commentId/replies", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al responder al comentario")

        val reply = result.getAsJsonObject("reply") ?: result
        val replyId = reply.get("id")?.asString ?: "desconocido"

        return CanvaToolResult(id, buildString {
            appendLine("Respuesta agregada exitosamente")
            appendLine("ID de la respuesta: $replyId")
            appendLine("Comentario original: $commentId")
            appendLine("Mensaje: $message")
        }.trim())
    }

    // ── Usuario ──────────────────────────────────────────────────────────────

    private suspend fun getUserProfile(id: String, @Suppress("UNUSED_PARAMETER") args: JsonObject, token: String): CanvaToolResult {
        val json = get("$API_URL/users/me", token)
            ?: return CanvaToolResult(id, "Error al obtener el perfil del usuario")

        val profile = json.getAsJsonObject("profile") ?: json

        return CanvaToolResult(id, buildString {
            appendLine("**Perfil de usuario de Canva**")
            val displayName = profile.get("display_name")?.asString
            if (!displayName.isNullOrBlank()) appendLine("Nombre: $displayName")
            val userId = profile.get("id")?.asString ?: json.get("id")?.asString
            if (!userId.isNullOrBlank()) appendLine("ID: $userId")
            val email = profile.get("email")?.asString
            if (!email.isNullOrBlank()) appendLine("Email: $email")
            val teamId = profile.get("team_id")?.asString ?: json.get("team_id")?.asString
            if (!teamId.isNullOrBlank()) appendLine("ID del equipo: $teamId")
        }.trim())
    }

    // ── Importar ─────────────────────────────────────────────────────────────

    private suspend fun importDesign(id: String, args: JsonObject, token: String): CanvaToolResult {
        val url = args.get("url")?.asString
            ?: return CanvaToolResult(id, "Parámetro 'url' requerido")
        val title = args.get("title")?.asString

        val jsonBody = buildString {
            append("{\"url\":${JsonPrimitive(url)}")
            if (!title.isNullOrBlank()) append(",\"title\":${JsonPrimitive(title)}")
            append("}")
        }

        val result = post("$API_URL/imports", jsonBody, token)
            ?: return CanvaToolResult(id, "Error al importar el diseño a Canva")

        val job = result.getAsJsonObject("job") ?: result
        val importId = job.get("id")?.asString ?: "desconocido"
        val designId = job.get("design_id")?.asString
            ?: job.getAsJsonObject("result")?.getAsJsonObject("design")?.get("id")?.asString

        return CanvaToolResult(id, buildString {
            appendLine("Importación iniciada exitosamente")
            appendLine("ID del trabajo: $importId")
            if (!designId.isNullOrBlank()) appendLine("ID del diseño: $designId")
            if (!title.isNullOrBlank()) appendLine("Título: $title")
            appendLine("URL origen: $url")
        }.trim())
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private suspend fun get(url: String, token: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET $url -> ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "GET error", e); null }
    }

    private suspend fun post(url: String, body: String, token: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val respBody = resp.body?.string() ?: ""
            if (resp.code !in 200..299) { Log.e(TAG, "POST $url -> ${resp.code}: $respBody"); return@withContext null }
            if (respBody.isBlank()) return@withContext JsonObject()
            JsonParser.parseString(respBody).asJsonObject
        } catch (e: Exception) { Log.e(TAG, "POST error", e); null }
    }

    private suspend fun deleteNoBody(url: String, token: String): Int = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().code
        } catch (e: Exception) { Log.e(TAG, "DELETE error", e); -1 }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
