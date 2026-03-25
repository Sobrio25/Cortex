package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.repository.FileRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class FileToolResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String,
    val mimeType: String? = null
)

@Singleton
class FileToolHandler @Inject constructor(
    private val fileRepository: FileRepository
) {
    companion object {
        private const val TAG = "FileToolHandler"

        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "xml", "csv", "html", "htm", "css",
            "js", "ts", "py", "kt", "java", "sql", "sh", "yaml", "yml",
            "toml", "ini", "cfg", "conf", "log", "properties", "gradle",
            "bat", "ps1", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "swift", "dart", "lua", "r", "m", "mm"
        )

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

        private val IMAGE_MIME_MAP = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "bmp" to "image/bmp"
        )

        fun getToolDefinitionsJson(workspacePath: String? = null): List<Map<String, Any>> {
            val pathNote = if (workspacePath != null) " Workspace: $workspacePath" else ""

            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "read_text_file",
                        "description" to "Read a text file from workspace.$pathNote",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "file_name" to mapOf(
                                    "type" to "string",
                                    "description" to "Nombre del archivo a leer (relativo al workspace)"
                                )
                            ),
                            "required" to listOf("file_name")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "read_image_file",
                        "description" to "Read an image from workspace as base64.$pathNote",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "file_name" to mapOf(
                                    "type" to "string",
                                    "description" to "Nombre del archivo de imagen a leer"
                                )
                            ),
                            "required" to listOf("file_name")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "read_pdf_file",
                        "description" to "Read a PDF file from workspace.$pathNote",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "file_name" to mapOf(
                                    "type" to "string",
                                    "description" to "Nombre del archivo PDF a leer"
                                )
                            ),
                            "required" to listOf("file_name")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "write_file",
                        "description" to "Create or overwrite a file in workspace.$pathNote",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "file_name" to mapOf(
                                    "type" to "string",
                                    "description" to "Nombre del archivo a crear/sobrescribir"
                                ),
                                "content" to mapOf(
                                    "type" to "string",
                                    "description" to "Contenido a escribir en el archivo"
                                )
                            ),
                            "required" to listOf("file_name", "content")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "list_files",
                        "description" to "List all files in workspace with name, size, and MIME type.$pathNote",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to emptyMap<String, Any>()
                        )
                    )
                )
            )
        }
    }

    private val gson = Gson()

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String, workspaceId: Long): FileToolResult {
        return try {
            val args = gson.fromJson(arguments, JsonObject::class.java) ?: JsonObject()
            when (toolName) {
                "read_text_file" -> readTextFile(toolCallId, args, workspaceId)
                "read_image_file" -> readImageFile(toolCallId, args, workspaceId)
                "read_pdf_file" -> readPdfFile(toolCallId, args, workspaceId)
                "write_file" -> writeFile(toolCallId, args, workspaceId)
                "list_files" -> listFiles(toolCallId, workspaceId)
                else -> FileToolResult(
                    toolCallId = toolCallId,
                    toolName = toolName,
                    success = false,
                    content = "Tool desconocida: $toolName"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing file tool: $toolName", e)
            FileToolResult(
                toolCallId = toolCallId,
                toolName = toolName,
                success = false,
                content = "Error ejecutando $toolName: ${e.message}"
            )
        }
    }

    private suspend fun readTextFile(toolCallId: String, args: JsonObject, workspaceId: Long): FileToolResult {
        val fileName = args.get("file_name")?.asString
            ?: return FileToolResult(toolCallId, "read_text_file", false, "Parámetro 'file_name' requerido")

        val ext = fileName.substringAfterLast(".", "").lowercase()
        if (ext !in TEXT_EXTENSIONS) {
            return FileToolResult(toolCallId, "read_text_file", false,
                "Extensión '$ext' no soportada para lectura de texto. Extensiones soportadas: ${TEXT_EXTENSIONS.joinToString(", ")}")
        }

        val content = fileRepository.readFileContent(workspaceId, fileName)
            ?: return FileToolResult(toolCallId, "read_text_file", false, "Archivo no encontrado: $fileName")

        return FileToolResult(
            toolCallId = toolCallId,
            toolName = "read_text_file",
            success = true,
            content = content
        )
    }

    private suspend fun readImageFile(toolCallId: String, args: JsonObject, workspaceId: Long): FileToolResult {
        val fileName = args.get("file_name")?.asString
            ?: return FileToolResult(toolCallId, "read_image_file", false, "Parámetro 'file_name' requerido")

        val ext = fileName.substringAfterLast(".", "").lowercase()
        if (ext !in IMAGE_EXTENSIONS) {
            return FileToolResult(toolCallId, "read_image_file", false,
                "Extensión '$ext' no soportada para lectura de imagen. Extensiones soportadas: ${IMAGE_EXTENSIONS.joinToString(", ")}")
        }

        val base64 = fileRepository.readFileAsBase64(workspaceId, fileName)
            ?: return FileToolResult(toolCallId, "read_image_file", false, "Archivo no encontrado: $fileName")

        val mimeType = IMAGE_MIME_MAP[ext] ?: "image/png"
        val dataUri = "data:$mimeType;base64,$base64"

        return FileToolResult(
            toolCallId = toolCallId,
            toolName = "read_image_file",
            success = true,
            content = dataUri,
            mimeType = mimeType
        )
    }

    private suspend fun readPdfFile(toolCallId: String, args: JsonObject, workspaceId: Long): FileToolResult {
        val fileName = args.get("file_name")?.asString
            ?: return FileToolResult(toolCallId, "read_pdf_file", false, "Parámetro 'file_name' requerido")

        if (!fileName.lowercase().endsWith(".pdf")) {
            return FileToolResult(toolCallId, "read_pdf_file", false, "El archivo debe tener extensión .pdf")
        }

        val text = fileRepository.readPdfText(workspaceId, fileName)
            ?: return FileToolResult(toolCallId, "read_pdf_file", false, "Archivo PDF no encontrado: $fileName")

        return FileToolResult(
            toolCallId = toolCallId,
            toolName = "read_pdf_file",
            success = true,
            content = text
        )
    }

    private suspend fun writeFile(toolCallId: String, args: JsonObject, workspaceId: Long): FileToolResult {
        val fileName = args.get("file_name")?.asString
            ?: return FileToolResult(toolCallId, "write_file", false, "Parámetro 'file_name' requerido")
        val content = args.get("content")?.asString
            ?: return FileToolResult(toolCallId, "write_file", false, "Parámetro 'content' requerido")

        val result = fileRepository.saveGeneratedFile(workspaceId, fileName, content)

        return result.fold(
            onSuccess = { file ->
                FileToolResult(
                    toolCallId = toolCallId,
                    toolName = "write_file",
                    success = true,
                    content = "Archivo '$fileName' creado/actualizado exitosamente (${file.length()} bytes)"
                )
            },
            onFailure = { error ->
                FileToolResult(
                    toolCallId = toolCallId,
                    toolName = "write_file",
                    success = false,
                    content = "Error escribiendo archivo '$fileName': ${error.message}"
                )
            }
        )
    }

    private suspend fun listFiles(toolCallId: String, workspaceId: Long): FileToolResult {
        val files = fileRepository.listFiles(workspaceId)

        if (files.isEmpty()) {
            return FileToolResult(
                toolCallId = toolCallId,
                toolName = "list_files",
                success = true,
                content = "El workspace está vacío. No hay archivos."
            )
        }

        val fileList = files.joinToString("\n") { file ->
            val size = formatFileSize(file.length())
            val ext = file.name.substringAfterLast(".", "")
            val mime = getMimeType(ext)
            "- ${file.name} ($size, $mime)"
        }

        return FileToolResult(
            toolCallId = toolCallId,
            toolName = "list_files",
            success = true,
            content = "Archivos en el workspace (${files.size}):\n$fileList"
        )
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun getMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "ts" -> "application/typescript"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
