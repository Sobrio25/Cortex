package com.aiagents.app.data.local

import android.content.Context
import android.util.Log
import com.aiagents.app.data.model.CustomLocalModelEntity
import com.aiagents.app.domain.model.LocalModel
import com.aiagents.app.domain.model.RecommendedModels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences,
    private val customLocalModelDao: CustomLocalModelDao
) {
    private val TAG = "LocalModelRepository"

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: Flow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isDownloading: Flow<Map<String, Boolean>> = _isDownloading.asStateFlow()

    // Cliente HTTP con timeouts extendidos para descargas grandes
    // readTimeout: tiempo máximo sin recibir datos (no tiempo total de descarga)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // 5 min sin datos
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getAvailableModels(): List<LocalModel> {
        val recommended = RecommendedModels.MODELS.map { model ->
            val localFile = File(modelsDir, model.fileName)
            model.copy(
                isDownloaded = localFile.exists() && localFile.length() > 0,
                localPath = if (localFile.exists() && localFile.length() > 0) localFile.absolutePath else null
            )
        }

        // Load custom models from Room (quick query on small table)
        val customModels = runBlocking(Dispatchers.IO) {
            customLocalModelDao.getAllSync()
        }.map { entity ->
            val localFile = File(modelsDir, entity.fileName)
            val isComplete = localFile.exists() &&
                localFile.length() > 0 &&
                (entity.sizeBytes <= 0 || localFile.length() == entity.sizeBytes)
            LocalModel(
                id = entity.id,
                name = entity.name,
                huggingFaceUrl = entity.huggingFaceUrl,
                fileName = entity.fileName,
                sizeBytes = entity.sizeBytes,
                description = entity.description,
                contextLength = entity.contextLength,
                isDownloaded = isComplete,
                localPath = if (isComplete) localFile.absolutePath else null,
                requiresLicense = entity.requiresLicense,
                requiresHFToken = entity.requiresHFToken
            )
        }

        return recommended + customModels
    }

    suspend fun addCustomModel(entity: CustomLocalModelEntity) {
        customLocalModelDao.insert(entity)
    }

    suspend fun removeCustomModel(id: String) {
        customLocalModelDao.delete(id)
    }

    fun getDownloadedModels(): List<LocalModel> {
        return getAvailableModels().filter { it.isDownloaded }
    }

    fun hasHuggingFaceToken(): Boolean = securePreferences.hasHuggingFaceToken()

    fun getHuggingFaceToken(): String? = securePreferences.getHuggingFaceToken()

    fun saveHuggingFaceToken(token: String) = securePreferences.saveHuggingFaceToken(token)

    fun removeHuggingFaceToken() = securePreferences.removeHuggingFaceToken()

    suspend fun downloadModel(
        model: LocalModel,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (model.huggingFaceUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Este modelo no tiene URL de descarga. Usa 'Importar Modelo' para añadirlo manualmente.")
            )
        }

        try {
            _isDownloading.value = _isDownloading.value + (model.id to true)

            val outputFile = File(modelsDir, model.fileName)

            // Si existe un archivo parcial, eliminarlo
            if (outputFile.exists()) {
                outputFile.delete()
            }

            Log.d(TAG, "Iniciando descarga de: ${model.huggingFaceUrl}")

            // Construir request con headers necesarios
            val requestBuilder = Request.Builder()
                .url(model.huggingFaceUrl)
                .header("User-Agent", "Cortex-Android/0.2.0")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")

            // Agregar token de HuggingFace si está disponible
            val hfToken = securePreferences.getHuggingFaceToken()
            if (!hfToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $hfToken")
                Log.d(TAG, "Usando token de HuggingFace para la descarga")
            } else if (model.requiresHFToken) {
                Log.w(TAG, "Modelo requiere token de HF pero no está configurado")
            }

            val request = requestBuilder.get().build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val code = response.code
                val message = when (code) {
                    401 -> "HTTP 401: Token de HuggingFace inválido o expirado."
                    403 -> "HTTP 403: Acceso denegado. Necesitas aceptar la licencia en HuggingFace y configurar tu token."
                    404 -> "HTTP 404: Modelo no encontrado. La URL puede haber cambiado."
                    429 -> "HTTP 429: Demasiadas solicitudes. Espera unos minutos y vuelve a intentarlo."
                    else -> "HTTP $code: ${response.message}"
                }
                throw IOException(message)
            }

            val body = response.body
                ?: throw IOException("Respuesta vacía del servidor")

            val totalSize = body.contentLength()
            Log.d(TAG, "Tamaño total: $totalSize bytes")

            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(32768) // Buffer de 32KB para mejor rendimiento
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = if (totalSize > 0) {
                            totalBytesRead.toFloat() / totalSize
                        } else {
                            // Si no sabemos el tamaño, simular progreso gradual
                            (totalBytesRead.toFloat() / model.sizeBytes).coerceIn(0f, 0.99f)
                        }

                        _downloadProgress.value = _downloadProgress.value + (model.id to progress)
                        onProgress(progress)
                    }
                }
            }

            // Verificar que el archivo se descargó correctamente
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IOException("El archivo descargado está vacío o no existe")
            }

            Log.d(TAG, "Descarga completada: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            _isDownloading.value = _isDownloading.value - model.id
            _downloadProgress.value = _downloadProgress.value - model.id

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo: ${e.message}", e)
            _isDownloading.value = _isDownloading.value - model.id
            _downloadProgress.value = _downloadProgress.value - model.id

            // Limpiar archivo parcial si existe
            try {
                val outputFile = File(modelsDir, model.fileName)
                if (outputFile.exists() && (model.sizeBytes == 0L || outputFile.length() < model.sizeBytes)) {
                    outputFile.delete()
                }
            } catch (_: Exception) {}

            Result.failure(e)
        }
    }

    fun deleteModel(model: LocalModel): Boolean {
        val file = File(modelsDir, model.fileName)
        val partialFile = File(modelsDir, "${model.fileName}.part")
        val deletedModel = !file.exists() || file.delete()
        val deletedPartial = !partialFile.exists() || partialFile.delete()
        return deletedModel && deletedPartial
    }

    fun getModelPath(fileName: String): String {
        return File(modelsDir, fileName).absolutePath
    }

    fun modelExists(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return file.exists() && file.length() > 0
    }

    fun getAvailableStorage(): Long {
        return context.filesDir.freeSpace
    }

    fun importModelFromFile(sourceFile: File, targetFileName: String): Result<String> {
        return try {
            val destFile = File(modelsDir, targetFileName)
            sourceFile.copyTo(destFile, overwrite = true)
            if (destFile.exists() && destFile.length() > 0) {
                Result.success(destFile.absolutePath)
            } else {
                Result.failure(IOException("Error al copiar el archivo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
