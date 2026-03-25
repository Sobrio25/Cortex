package com.aiagents.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.aiagents.app.domain.model.AgentFile
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workspaceDao: com.aiagents.app.data.local.WorkspaceDao
) {
    companion object {
        private const val WORKSPACE_ROOT = "aiagents_workspace"
        private const val TAG = "FileRepository"
    }

    /**
     * Validates and sanitizes a file path to prevent path traversal attacks.
     * Ensures the resolved path stays within the workspace directory.
     * Supports subdirectories (e.g. "src/components/App.tsx").
     * Returns null if the path is invalid or escapes the workspace.
     */
    private fun validatePath(workspaceFolder: File, fileName: String): File? {
        // Reject obviously malicious patterns
        val normalized = fileName.replace("\\", "/")
        if (normalized.startsWith("/") || normalized.contains("/../") || normalized.startsWith("../")) {
            Log.w(TAG, "Path traversal rejected: $fileName")
            return null
        }

        val resolved = File(workspaceFolder, normalized).canonicalFile
        val workspaceCanonical = workspaceFolder.canonicalFile

        // Ensure resolved path is inside the workspace
        if (!resolved.path.startsWith(workspaceCanonical.path + File.separator) && resolved != workspaceCanonical) {
            Log.w(TAG, "Path escaped workspace: $fileName → ${resolved.path} (workspace: ${workspaceCanonical.path})")
            return null
        }

        return resolved
    }

    /**
     * Finds a file in an external (SAF) folder, supporting subdirectories.
     * For "src/components/App.tsx", navigates through src → components → App.tsx.
     */
    private fun findFileInExternal(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.replace("\\", "/").split("/").filter { it.isNotEmpty() }
        var current = root
        for ((i, part) in parts.withIndex()) {
            val isLast = i == parts.lastIndex
            val child = current.findFile(part)
            if (child == null) return null
            if (!isLast && child.isDirectory) {
                current = child
            } else if (isLast) {
                return child
            } else {
                return null // intermediate segment is not a directory
            }
        }
        return null
    }

    /**
     * Creates a file in an external (SAF) folder, creating subdirectories as needed.
     * For "src/components/App.tsx", creates src/ and components/ directories first.
     */
    private fun createFileInExternal(root: DocumentFile, path: String, mimeType: String): DocumentFile? {
        val normalized = path.replace("\\", "/")
        val parts = normalized.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        // Navigate/create subdirectories
        var current = root
        for (i in 0 until parts.size - 1) {
            val dirName = parts[i]
            val existing = current.findFile(dirName)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else {
                current.createDirectory(dirName) ?: return null
            }
        }

        // Delete existing file if present, then create
        val fileName = parts.last()
        current.findFile(fileName)?.delete()
        return current.createFile(mimeType, fileName)
    }

    private fun getWorkspaceRootFolder(): File {
        // Usar directorio interno de la app para evitar problemas de permisos con Scoped Storage
        // El directorio interno (/data/data/<package>/files/) es accesible desde la shell
        val folder = File(context.filesDir, WORKSPACE_ROOT)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        // Asegurar permisos en todo el árbol de directorios
        ensureDirectoryPermissions(folder)
        Log.v("FileRepository", "Using workspace root: ${folder.absolutePath}")
        return folder
    }

    private fun ensureDirectoryPermissions(directory: File) {
        // Establecer permisos 755 (rwxr-xr-x) recursivamente en todos los directorios
        var current: File? = directory
        while (current != null && current.absolutePath.startsWith(context.filesDir.absolutePath)) {
            current.setReadable(true, false)
            current.setWritable(true, true)
            current.setExecutable(true, false)
            current = current.parentFile
        }
    }

    private suspend fun getWorkspaceFolder(workspaceId: Long): File {
        // Obtener el nombre del workspace desde la base de datos
        val workspace = workspaceDao.getWorkspaceById(workspaceId)
        val folderName = if (workspace != null) {
            // Sanitizar el nombre del workspace para usarlo como nombre de carpeta
            sanitizeFolderName(workspace.name)
        } else {
            // Fallback al ID si no se encuentra el workspace
            "workspace_$workspaceId"
        }

        val folder = File(getWorkspaceRootFolder(), folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        // Asegurar permisos en todo el árbol
        ensureDirectoryPermissions(folder)
        Log.v("FileRepository", "Workspace folder: ${folder.absolutePath}, canWrite: ${folder.canWrite()}")
        return folder
    }

    /**
     * Obtiene el DocumentFile de la carpeta externa del workspace, si está configurada y accesible.
     * Retorna null si el workspace usa almacenamiento interno, es __global__, o la carpeta no es accesible.
     */
    private suspend fun getExternalFolder(workspaceId: Long): DocumentFile? {
        val workspace = workspaceDao.getWorkspaceById(workspaceId) ?: return null
        if (workspace.name == "__global__") return null
        val uriString = workspace.externalStorageUri ?: return null
        return try {
            val uri = Uri.parse(uriString)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.exists() && docFile.canWrite()) docFile else null
        } catch (e: Exception) {
            Log.w(TAG, "External folder not accessible for workspace $workspaceId: ${e.message}")
            null
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun sanitizeFolderName(name: String): String {
        // Remover caracteres no válidos para nombres de archivo/carpeta
        // Reemplazar espacios con guiones bajos
        // Limitar a 50 caracteres para evitar nombres muy largos
        val sanitized = name
            .trim()
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ\\s-]"), "") // Permitir letras, números, espacios y guiones
            .replace(Regex("\\s+"), "_") // Reemplazar espacios con guiones bajos
            .take(50) // Limitar longitud

        // Si queda vacío después de sanitizar, usar un nombre genérico
        return if (sanitized.isBlank()) "workspace_${System.currentTimeMillis()}" else sanitized
    }

    suspend fun copyFileToWorkspaceFolder(
        workspaceId: Long,
        uri: Uri,
        fileName: String
    ): Result<File> {
        return try {
            val externalFolder = getExternalFolder(workspaceId)
            if (externalFolder != null) {
                // Almacenamiento externo via SAF
                val mimeType = getMimeType(fileName)
                val newDoc = externalFolder.createFile(mimeType, fileName)
                    ?: return Result.failure(Exception("No se pudo crear archivo en carpeta externa"))
                var bytesCopied = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(newDoc.uri)?.use { output ->
                        bytesCopied = input.copyTo(output)
                    }
                }
                Result.success(ExternalFileProxy(
                    displayName = newDoc.name ?: fileName,
                    fileSize = bytesCopied,
                    modified = System.currentTimeMillis(),
                    documentUri = newDoc.uri.toString()
                ))
            } else {
                // Almacenamiento interno (original)
                val folder = getWorkspaceFolder(workspaceId)
                val file = File(folder, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.setReadable(true, false)
                file.setWritable(true, true)
                Result.success(file)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveGeneratedFile(
        workspaceId: Long,
        fileName: String,
        content: String
    ): Result<File> {
        return try {
            val externalFolder = getExternalFolder(workspaceId)
            if (externalFolder != null) {
                val mimeType = getMimeType(fileName)
                val doc = createFileInExternal(externalFolder, fileName, mimeType)
                    ?: return Result.failure(Exception("No se pudo crear archivo en carpeta externa: $fileName"))
                val bytes = content.toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                    output.write(bytes)
                }
                Result.success(ExternalFileProxy(
                    displayName = doc.name ?: fileName,
                    fileSize = bytes.size.toLong(),
                    modified = System.currentTimeMillis(),
                    documentUri = doc.uri.toString()
                ))
            } else {
                val folder = getWorkspaceFolder(workspaceId)
                val file = validatePath(folder, fileName)
                    ?: return Result.failure(Exception("Ruta inválida: $fileName"))
                // Create parent directories if needed (e.g. src/components/)
                file.parentFile?.mkdirs()
                file.writeText(content)
                file.setReadable(true, false)
                file.setWritable(true, true)
                Result.success(file)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(workspaceId: Long, fileName: String): Boolean {
        val externalFolder = getExternalFolder(workspaceId)
        if (externalFolder != null) {
            val doc = findFileInExternal(externalFolder, fileName)
            return doc?.delete() ?: false
        }
        val folder = getWorkspaceFolder(workspaceId)
        val file = validatePath(folder, fileName) ?: return false
        return file.delete()
    }

    suspend fun readFileContent(workspaceId: Long, fileName: String): String? {
        val externalFolder = getExternalFolder(workspaceId)
        if (externalFolder != null) {
            val doc = findFileInExternal(externalFolder, fileName) ?: return null
            return try {
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    input.bufferedReader().readText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading external file: $fileName", e)
                null
            }
        }
        val folder = getWorkspaceFolder(workspaceId)
        val file = validatePath(folder, fileName) ?: return null
        return if (file.exists()) file.readText() else null
    }

    suspend fun listFiles(workspaceId: Long): List<File> {
        val externalFolder = getExternalFolder(workspaceId)
        if (externalFolder != null) {
            return externalFolder.listFiles()
                .filter { it.isFile }
                .mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    val size = doc.length()
                    val lastMod = doc.lastModified()
                    // Create a File pointing to a virtual path that preserves name and metadata.
                    // Callers use file.name, file.length(), file.lastModified().
                    // We use a temp dir so File.length() returns 0 but we store real size in name convention.
                    // Better approach: write a tiny placeholder so metadata works.
                    ExternalFileProxy(name, size, lastMod, doc.uri.toString())
                }
        }
        val folder = getWorkspaceFolder(workspaceId)
        return folder.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
    }

    /**
     * A File subclass that correctly reports name/size/lastModified for SAF DocumentFiles.
     * Used only by listFiles() for UI display — NOT for actual I/O.
     */
    class ExternalFileProxy(
        private val displayName: String,
        private val fileSize: Long,
        private val modified: Long,
        val documentUri: String
    ) : File(documentUri) {
        override fun getName(): String = displayName
        override fun length(): Long = fileSize
        override fun lastModified(): Long = modified
        override fun exists(): Boolean = true
        override fun isFile(): Boolean = true
        override fun isDirectory(): Boolean = false
        override fun getAbsolutePath(): String = documentUri
    }

    suspend fun getFileInfo(workspaceId: Long, fileName: String): File? {
        val externalFolder = getExternalFolder(workspaceId)
        if (externalFolder != null) {
            val doc = findFileInExternal(externalFolder, fileName) ?: return null
            return File(doc.uri.toString())
        }
        val folder = getWorkspaceFolder(workspaceId)
        val file = validatePath(folder, fileName) ?: return null
        return if (file.exists()) file else null
    }

    suspend fun getWorkspaceFolderPath(workspaceId: Long): String {
        // Siempre retorna el path interno (necesario para shell)
        return getWorkspaceFolder(workspaceId).absolutePath
    }

    suspend fun readFileAsBase64(workspaceId: Long, fileName: String): String? {
        return try {
            val externalFolder = getExternalFolder(workspaceId)
            if (externalFolder != null) {
                val doc = findFileInExternal(externalFolder, fileName) ?: return null
                val ext = fileName.substringAfterLast(".", "").lowercase()
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (ext in setOf("jpg", "jpeg", "png", "webp", "bmp")) {
                        compressImageBytesToBase64(bytes, ext)
                    } else {
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }
            } else {
                val folder = getWorkspaceFolder(workspaceId)
                val file = validatePath(folder, fileName) ?: return null
                if (!file.exists()) return null
                val ext = fileName.substringAfterLast(".", "").lowercase()
                if (ext in setOf("jpg", "jpeg", "png", "webp", "bmp")) {
                    compressImageToBase64(file, ext)
                } else {
                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file as base64: $fileName", e)
            null
        }
    }

    private fun compressImageBytesToBase64(bytes: ByteArray, ext: String): String? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

            val maxDim = 1024
            var sampleSize = 1
            var w = boundsOpts.outWidth
            var h = boundsOpts.outHeight
            while (w > maxDim * 2 || h > maxDim * 2) {
                sampleSize *= 2; w /= 2; h /= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null

            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                bitmap.recycle()
                scaled
            } else {
                bitmap
            }

            val out = ByteArrayOutputStream()
            val format = if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (ext == "png") 100 else 80
            scaledBitmap.compress(format, quality, out)
            scaledBitmap.recycle()

            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image bytes", e)
            null
        }
    }

    private fun compressImageToBase64(file: File, ext: String): String? {
        return try {
            // Two-pass decoding: first read dimensions only, then decode with subsampling
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)

            val maxDim = 1024
            var sampleSize = 1
            var w = boundsOpts.outWidth
            var h = boundsOpts.outHeight
            while (w > maxDim * 2 || h > maxDim * 2) {
                sampleSize *= 2; w /= 2; h /= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                bitmap.recycle()
                scaled
            } else {
                bitmap
            }

            val out = ByteArrayOutputStream()
            val format = if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (ext == "png") 100 else 80
            scaledBitmap.compress(format, quality, out)
            scaledBitmap.recycle()

            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${file.name}", e)
            null
        }
    }

    suspend fun readPdfText(workspaceId: Long, fileName: String): String? {
        val externalFolder = getExternalFolder(workspaceId)
        if (externalFolder != null) {
            val doc = findFileInExternal(externalFolder, fileName) ?: return null
            var renderer: PdfRenderer? = null
            return try {
                context.contentResolver.openFileDescriptor(doc.uri, "r")?.use { pfd ->
                    renderer = PdfRenderer(pfd)
                    val pageCount = renderer!!.pageCount
                    val sb = StringBuilder()
                    sb.appendLine("PDF: $fileName")
                    sb.appendLine("Páginas: $pageCount")
                    sb.appendLine()
                    sb.appendLine("Nota: La extracción de texto de PDFs requiere una librería especializada.")
                    sb.appendLine("Este PDF tiene $pageCount página(s). Para analizar el contenido visual,")
                    sb.appendLine("convierte las páginas a imágenes y usa read_image_file.")
                    for (i in 0 until minOf(pageCount, 20)) {
                        val page = renderer!!.openPage(i)
                        sb.appendLine("--- Página ${i + 1} de $pageCount (${page.width}x${page.height}px) ---")
                        page.close()
                    }
                    if (pageCount > 20) sb.appendLine("... y ${pageCount - 20} páginas más.")
                    sb.toString()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading external PDF: $fileName", e)
                "Error al leer el PDF: ${e.message}"
            } finally {
                try { renderer?.close() } catch (_: Throwable) {}
            }
        }

        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            val folder = getWorkspaceFolder(workspaceId)
            val file = validatePath(folder, fileName) ?: return null
            if (!file.exists()) return null
            if (file.length() == 0L) return "El archivo PDF está vacío."
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            val pageCount = renderer.pageCount
            val sb = StringBuilder()
            sb.appendLine("PDF: $fileName")
            sb.appendLine("Páginas: $pageCount")
            sb.appendLine()
            sb.appendLine("Nota: La extracción de texto de PDFs requiere una librería especializada.")
            sb.appendLine("Este PDF tiene $pageCount página(s). Para analizar el contenido visual,")
            sb.appendLine("convierte las páginas a imágenes y usa read_image_file.")
            for (i in 0 until minOf(pageCount, 20)) {
                val page = renderer.openPage(i)
                sb.appendLine("--- Página ${i + 1} de $pageCount (${page.width}x${page.height}px) ---")
                page.close()
            }
            if (pageCount > 20) sb.appendLine("... y ${pageCount - 20} páginas más.")
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PDF: $fileName", e)
            "Error al leer el PDF: ${e.message}"
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error reading PDF: $fileName", t)
            "Error fatal al procesar el PDF. El archivo puede estar dañado o en un formato no compatible."
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { fd?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Lee una imagen desde una URI y la convierte a Base64 con prefijo data URI.
     * Comprime la imagen si es necesario para reducir el tamaño.
     */
    suspend fun readImageUriAsBase64(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

                // Si es una imagen, comprimirla si es necesario
                if (mimeType.startsWith("image/")) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val compressed = compressBitmapToBase64(bitmap, mimeType)
                        bitmap.recycle()
                        compressed
                    } else {
                        // Si no se puede decodificar como bitmap, devolver los bytes originales
                        "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
                    }
                } else {
                    "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading image URI as base64: $uri", e)
            null
        }
    }

    /**
     * Lee el texto de un PDF desde una URI.
     * Extrae información básica del PDF (páginas, dimensiones).
     */
    suspend fun readPdfUriAsText(uri: Uri): String? {
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                fd = pfd
                renderer = PdfRenderer(pfd)
                val pageCount = renderer!!.pageCount
                val fileName = uri.lastPathSegment ?: "documento.pdf"
                val sb = StringBuilder()
                sb.appendLine("PDF: $fileName")
                sb.appendLine("Páginas: $pageCount")
                sb.appendLine()
                sb.appendLine("Nota: Este es un resumen del PDF. Para analizar el contenido completo,")
                sb.appendLine("considera convertir las páginas relevantes a imágenes.")
                sb.appendLine()
                for (i in 0 until minOf(pageCount, 10)) {
                    val page = renderer!!.openPage(i)
                    sb.appendLine("--- Página ${i + 1} de $pageCount (${page.width}x${page.height}px) ---")
                    page.close()
                }
                if (pageCount > 10) {
                    sb.appendLine("... y ${pageCount - 10} páginas más.")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PDF URI: $uri", e)
            "Error al leer el PDF: ${e.message}"
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error reading PDF URI: $uri", t)
            "Error fatal al procesar el PDF."
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Lee el contenido de texto de un archivo desde una URI.
     */
    suspend fun readTextFileFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading text file from URI: $uri", e)
            null
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap, mimeType: String): String {
        val maxDim = 1024
        val sampleSize = calculateInSampleSize(bitmap.width, bitmap.height, maxDim)

        val scaledBitmap = if (sampleSize > 1 || bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = if (sampleSize > 1) {
                1f / sampleSize
            } else {
                maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            }
            val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        val format = if (mimeType.contains("png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (mimeType.contains("png")) 100 else 85
        scaledBitmap.compress(format, quality, out)

        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        val base64Data = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:$mimeType;base64,$base64Data"
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > maxDim * 2 || height / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun detectCodeBlocks(content: String): List<CodeBlock> {
        val codeBlockRegex = """```(\w+)?\n([\s\S]*?)```""".toRegex()
        return codeBlockRegex.findAll(content).map { match ->
            CodeBlock(
                language = match.groupValues[1].ifEmpty { "txt" },
                code = match.groupValues[2].trim()
            )
        }.toList()
    }

    fun generateFileName(language: String): String {
        val extension = when (language.lowercase()) {
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "xml" -> "xml"
            "sql" -> "sql"
            "shell", "bash", "sh" -> "sh"
            "markdown", "md" -> "md"
            else -> "txt"
        }
        val timestamp = System.currentTimeMillis()
        return "generated_$timestamp.$extension"
    }

    /**
     * Prepara el contexto raw del workspace (conversaciones, archivos, metadata)
     * para que el LLM lo analice y genere un CLAUDE.md inteligente.
     */
    fun buildWorkspaceRawContext(
        workspaceName: String,
        workspaceDescription: String,
        messages: List<Message>,
        files: List<AgentFile>,
        agentName: String?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=== WORKSPACE METADATA ===")
        sb.appendLine("Name: $workspaceName")
        if (workspaceDescription.isNotBlank()) {
            sb.appendLine("Description: $workspaceDescription")
        }
        sb.appendLine("Assigned Agent: ${agentName ?: "None"}")
        sb.appendLine("Total Messages: ${messages.size}")
        sb.appendLine("Total Files: ${files.size}")
        sb.appendLine("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine()

        if (files.isNotEmpty()) {
            sb.appendLine("=== FILES IN WORKSPACE ===")
            files.forEach { file ->
                sb.appendLine("- ${file.name} (${formatFileSize(file.size)}, type: ${file.mimeType})")
            }
            sb.appendLine()
        }

        if (messages.isNotEmpty()) {
            sb.appendLine("=== CONVERSATION HISTORY ===")
            messages.forEach { message ->
                val role = when (message.role) {
                    MessageRole.USER -> "USER"
                    MessageRole.ASSISTANT -> "ASSISTANT"
                    MessageRole.SYSTEM -> "SYSTEM"
                    MessageRole.TOOL -> "TOOL"
                }
                val content = message.content.take(3000)
                if (content.isNotBlank()) {
                    sb.appendLine("[$role]: $content")
                    if (message.toolCalls.isNotEmpty()) {
                        sb.appendLine("  [TOOL_CALLS]: ${message.toolCalls.joinToString { "${it.function.name}(${it.function.arguments.take(200)})" }}")
                    }
                    if (message.toolResults.isNotEmpty()) {
                        sb.appendLine("  [TOOL_RESULTS]: ${message.toolResults.joinToString { "${it.name}: ${it.content.take(200)}" }}")
                    }
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }

    /**
     * Escribe el AGENTES.md generado por el LLM en la carpeta del workspace.
     * Respects external storage if configured.
     */
    suspend fun writeCortexFile(workspaceId: Long, content: String): Result<File> {
        return try {
            val externalFolder = getExternalFolder(workspaceId)
            if (externalFolder != null) {
                // External storage via SAF
                val existing = externalFolder.findFile("AGENTES.md")
                existing?.delete()
                val doc = externalFolder.createFile("text/markdown", "AGENTES.md")
                    ?: return Result.failure(Exception("Could not create AGENTES.md in external folder"))
                val bytes = content.toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                    out.write(bytes)
                }
                Result.success(ExternalFileProxy(
                    displayName = "AGENTES.md",
                    fileSize = bytes.size.toLong(),
                    modified = System.currentTimeMillis(),
                    documentUri = doc.uri.toString()
                ))
            } else {
                val folder = getWorkspaceFolder(workspaceId)
                val cortexFile = File(folder, "AGENTES.md")
                cortexFile.writeText(content)
                Result.success(cortexFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}

data class CodeBlock(
    val language: String,
    val code: String
)
