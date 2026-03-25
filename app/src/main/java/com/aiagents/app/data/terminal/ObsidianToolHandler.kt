package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.JsonParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ObsidianToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class ObsidianToolHandler @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "ObsidianToolHandler"
        const val TOOL_READ_NOTE = "obsidian_read_note"
        const val TOOL_WRITE_NOTE = "obsidian_write_note"
        const val TOOL_SEARCH_NOTES = "obsidian_search_notes"
        const val TOOL_LIST_FOLDER = "obsidian_list_folder"
        const val TOOL_APPEND_NOTE = "obsidian_append_note"

        val ALL_TOOL_NAMES = setOf(
            TOOL_READ_NOTE, TOOL_WRITE_NOTE, TOOL_SEARCH_NOTES,
            TOOL_LIST_FOLDER, TOOL_APPEND_NOTE
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_READ_NOTE,
                        "description" to "Lee una nota de Obsidian. La ruta es relativa al vault. Ejemplo: 'Carpeta/Mi Nota.md' o 'Mi Nota' (se agrega .md automaticamente si no lo tiene).",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "path" to mapOf(
                                    "type" to "string",
                                    "description" to "Ruta relativa de la nota dentro del vault. Ejemplo: 'Proyectos/Mi Proyecto.md' o 'Daily/2026-03-05'"
                                )
                            ),
                            "required" to listOf("path")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_WRITE_NOTE,
                        "description" to "Crea o sobreescribe una nota en Obsidian. Si la carpeta no existe, se crea automaticamente. Soporta markdown completo y frontmatter YAML.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "path" to mapOf(
                                    "type" to "string",
                                    "description" to "Ruta relativa de la nota. Ejemplo: 'Proyectos/Nueva Nota.md'"
                                ),
                                "content" to mapOf(
                                    "type" to "string",
                                    "description" to "Contenido completo de la nota en formato markdown"
                                )
                            ),
                            "required" to listOf("path", "content")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_APPEND_NOTE,
                        "description" to "Agrega contenido al final de una nota existente en Obsidian. Util para agregar entradas a logs, diarios o listas sin sobreescribir el contenido existente.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "path" to mapOf(
                                    "type" to "string",
                                    "description" to "Ruta relativa de la nota"
                                ),
                                "content" to mapOf(
                                    "type" to "string",
                                    "description" to "Contenido a agregar al final de la nota"
                                )
                            ),
                            "required" to listOf("path", "content")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_SEARCH_NOTES,
                        "description" to "Busca notas en el vault de Obsidian por nombre de archivo o contenido. Devuelve las notas que coincidan con la consulta.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf(
                                    "type" to "string",
                                    "description" to "Texto a buscar en nombres de archivo y contenido de las notas"
                                ),
                                "folder" to mapOf(
                                    "type" to "string",
                                    "description" to "Carpeta opcional para limitar la busqueda. Ejemplo: 'Proyectos' o 'Daily'"
                                ),
                                "max_results" to mapOf(
                                    "type" to "integer",
                                    "description" to "Numero maximo de resultados (default 10, max 30)"
                                )
                            ),
                            "required" to listOf("query")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_LIST_FOLDER,
                        "description" to "Lista los archivos y subcarpetas de una carpeta del vault de Obsidian. Si no se especifica carpeta, lista la raiz del vault.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "folder" to mapOf(
                                    "type" to "string",
                                    "description" to "Carpeta relativa a listar. Dejar vacio para la raiz del vault."
                                )
                            ),
                            "required" to emptyList<String>()
                        )
                    )
                )
            )
        }
    }

    fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String
    ): ObsidianToolResult {
        val vaultPath = securePreferences.getObsidianVaultPath()
        if (vaultPath.isNullOrBlank()) {
            return ObsidianToolResult(
                toolCallId, false,
                "Error: No hay vault de Obsidian configurado. Pide al usuario que configure la ruta del vault en Configuracion > MCP."
            )
        }

        val vaultDir = File(vaultPath)
        if (!vaultDir.exists() || !vaultDir.isDirectory) {
            return ObsidianToolResult(
                toolCallId, false,
                "Error: El vault de Obsidian no existe en la ruta configurada: $vaultPath"
            )
        }

        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_READ_NOTE -> readNote(toolCallId, args, vaultDir)
                TOOL_WRITE_NOTE -> writeNote(toolCallId, args, vaultDir)
                TOOL_APPEND_NOTE -> appendNote(toolCallId, args, vaultDir)
                TOOL_SEARCH_NOTES -> searchNotes(toolCallId, args, vaultDir)
                TOOL_LIST_FOLDER -> listFolder(toolCallId, args, vaultDir)
                else -> ObsidianToolResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            ObsidianToolResult(toolCallId, false, "Error al ejecutar $toolName: ${e.message}")
        }
    }

    private fun readNote(
        toolCallId: String,
        args: com.google.gson.JsonObject,
        vaultDir: File
    ): ObsidianToolResult {
        val path = args.get("path")?.asString
            ?: return ObsidianToolResult(toolCallId, false, "Error: parametro 'path' requerido")

        val file = resolveNotePath(vaultDir, path)
        if (!file.exists()) {
            return ObsidianToolResult(toolCallId, false, "Nota no encontrada: $path")
        }

        val content = file.readText()
        val relativePath = file.relativeTo(vaultDir).path

        return ObsidianToolResult(
            toolCallId, true,
            buildString {
                appendLine("Nota: $relativePath")
                appendLine("Tamano: ${content.length} caracteres")
                appendLine("---")
                append(content)
            }
        )
    }

    private fun writeNote(
        toolCallId: String,
        args: com.google.gson.JsonObject,
        vaultDir: File
    ): ObsidianToolResult {
        val path = args.get("path")?.asString
            ?: return ObsidianToolResult(toolCallId, false, "Error: parametro 'path' requerido")
        val content = args.get("content")?.asString
            ?: return ObsidianToolResult(toolCallId, false, "Error: parametro 'content' requerido")

        val file = resolveNotePath(vaultDir, path)
        val isNew = !file.exists()

        // Create parent directories if needed
        file.parentFile?.mkdirs()
        file.writeText(content)

        val relativePath = file.relativeTo(vaultDir).path
        val action = if (isNew) "Nota creada" else "Nota actualizada"
        Log.d(TAG, "$action: $relativePath")

        return ObsidianToolResult(
            toolCallId, true,
            "$action exitosamente: $relativePath (${content.length} caracteres)"
        )
    }

    private fun appendNote(
        toolCallId: String,
        args: com.google.gson.JsonObject,
        vaultDir: File
    ): ObsidianToolResult {
        val path = args.get("path")?.asString
            ?: return ObsidianToolResult(toolCallId, false, "Error: parametro 'path' requerido")
        val content = args.get("content")?.asString
            ?: return ObsidianToolResult(toolCallId, false, "Error: parametro 'content' requerido")

        val file = resolveNotePath(vaultDir, path)
        if (!file.exists()) {
            return ObsidianToolResult(toolCallId, false, "Nota no encontrada: $path. Usa obsidian_write_note para crear una nueva.")
        }

        file.appendText("\n$content")
        val relativePath = file.relativeTo(vaultDir).path
        Log.d(TAG, "Contenido agregado a: $relativePath")

        return ObsidianToolResult(
            toolCallId, true,
            "Contenido agregado a: $relativePath (${content.length} caracteres anadidos)"
        )
    }

    private fun searchNotes(
        toolCallId: String,
        args: com.google.gson.JsonObject,
        vaultDir: File
    ): ObsidianToolResult {
        val query = args.get("query")?.asString
            ?: return ObsidianToolResult(toolCallId, false, "Error: parametro 'query' requerido")
        val folder = args.get("folder")?.asString
        val maxResults = args.get("max_results")?.asInt?.coerceIn(1, 30) ?: 10

        val searchDir = if (!folder.isNullOrBlank()) {
            File(vaultDir, folder).also {
                if (!it.exists()) return ObsidianToolResult(toolCallId, false, "Carpeta no encontrada: $folder")
            }
        } else {
            vaultDir
        }

        val queryLower = query.lowercase()
        val results = mutableListOf<Pair<File, String>>() // file to match context

        searchDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" && !it.path.contains(".obsidian") && !it.path.contains(".trash") }
            .forEach { file ->
                if (results.size >= maxResults) return@forEach

                val relativePath = file.relativeTo(vaultDir).path
                val nameMatch = file.nameWithoutExtension.lowercase().contains(queryLower)

                if (nameMatch) {
                    val preview = file.readText().take(200).replace("\n", " ").trim()
                    results.add(file to "Nombre coincide | Preview: $preview...")
                } else {
                    // Search in content
                    try {
                        val content = file.readText()
                        val idx = content.lowercase().indexOf(queryLower)
                        if (idx >= 0) {
                            val start = (idx - 50).coerceAtLeast(0)
                            val end = (idx + query.length + 100).coerceAtMost(content.length)
                            val context = content.substring(start, end).replace("\n", " ").trim()
                            results.add(file to "...${context}...")
                        }
                    } catch (e: Exception) {
                        // Skip files that can't be read
                    }
                }
            }

        if (results.isEmpty()) {
            return ObsidianToolResult(toolCallId, true, "No se encontraron notas para: \"$query\"")
        }

        val formatted = buildString {
            appendLine("Resultados de busqueda para: \"$query\"")
            if (!folder.isNullOrBlank()) appendLine("En carpeta: $folder")
            appendLine("${results.size} nota(s) encontrada(s):")
            appendLine()
            results.forEachIndexed { index, (file, context) ->
                val relativePath = file.relativeTo(vaultDir).path
                val size = file.length()
                appendLine("${index + 1}. **$relativePath** (${size} bytes)")
                appendLine("   $context")
                appendLine()
            }
        }

        return ObsidianToolResult(toolCallId, true, formatted.trim())
    }

    private fun listFolder(
        toolCallId: String,
        args: com.google.gson.JsonObject,
        vaultDir: File
    ): ObsidianToolResult {
        val folder = args.get("folder")?.asString
        val targetDir = if (!folder.isNullOrBlank()) {
            File(vaultDir, folder).also {
                if (!it.exists()) return ObsidianToolResult(toolCallId, false, "Carpeta no encontrada: $folder")
            }
        } else {
            vaultDir
        }

        val items = targetDir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        if (items.isEmpty()) {
            val folderName = folder ?: "raiz del vault"
            return ObsidianToolResult(toolCallId, true, "La carpeta '$folderName' esta vacia.")
        }

        val formatted = buildString {
            val folderName = folder ?: "raiz del vault"
            appendLine("Contenido de: $folderName")
            appendLine()

            val folders = items.filter { it.isDirectory }
            val files = items.filter { it.isFile }

            if (folders.isNotEmpty()) {
                appendLine("Carpetas (${folders.size}):")
                folders.forEach { dir ->
                    val noteCount = dir.walkTopDown().count { it.isFile && it.extension == "md" }
                    appendLine("  📁 ${dir.name}/ ($noteCount notas)")
                }
                appendLine()
            }

            if (files.isNotEmpty()) {
                appendLine("Archivos (${files.size}):")
                files.forEach { file ->
                    val size = when {
                        file.length() > 1024 * 1024 -> "${file.length() / (1024 * 1024)} MB"
                        file.length() > 1024 -> "${file.length() / 1024} KB"
                        else -> "${file.length()} bytes"
                    }
                    val icon = if (file.extension == "md") "📄" else "📎"
                    appendLine("  $icon ${file.name} ($size)")
                }
            }
        }

        return ObsidianToolResult(toolCallId, true, formatted.trim())
    }

    private fun resolveNotePath(vaultDir: File, path: String): File {
        val normalizedPath = if (path.endsWith(".md")) path else "$path.md"
        return File(vaultDir, normalizedPath)
    }
}
