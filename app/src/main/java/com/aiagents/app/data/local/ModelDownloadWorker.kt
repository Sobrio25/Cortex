package com.aiagents.app.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import com.aiagents.app.BuildConfig
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val securePreferences: SecurePreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "model_download"
        const val MODEL_TAG_PREFIX = "model_id:"
        const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID_BASE = 10000
        private const val TAG = "ModelDownloadWorker"

        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_EXACT_SIZE = "exact_size"

        const val PROGRESS_KEY = "progress"
        const val PROGRESS_BYTES_DOWNLOADED = "bytes_downloaded"
        const val PROGRESS_BYTES_TOTAL = "bytes_total"

        const val OUTPUT_ERROR_CODE = "error_code"
        const val OUTPUT_ERROR_MESSAGE = "error_message"

        fun modelTag(modelId: String): String = "$MODEL_TAG_PREFIX$modelId"

        fun modelIdFromTags(tags: Set<String>): String? = tags
            .firstOrNull { it.startsWith(MODEL_TAG_PREFIX) }
            ?.removePrefix(MODEL_TAG_PREFIX)
    }

    private class DownloadFailure(
        val errorCode: String,
        message: String,
        val retryable: Boolean = false
    ) : IOException(message)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return failure("invalid_input", "Falta el identificador del modelo")
        val modelName = inputData.getString(KEY_MODEL_NAME)
            ?: return failure("invalid_input", "Falta el nombre del modelo")
        val modelUrl = inputData.getString(KEY_MODEL_URL)
            ?: return failure("invalid_input", "Falta la URL del modelo")
        val fileName = inputData.getString(KEY_FILE_NAME)
            ?: return failure("invalid_input", "Falta el nombre del archivo")
        val sizeBytes = inputData.getLong(KEY_SIZE_BYTES, 0L)
        val exactSize = inputData.getBoolean(KEY_EXACT_SIZE, false)

        setForeground(createForegroundInfo(modelName, 0f))

        return try {
            downloadWithResume(
                modelId = modelId,
                modelName = modelName,
                modelUrl = modelUrl,
                fileName = fileName,
                expectedSize = sizeBytes,
                exactSize = exactSize
            )
            Result.success()
        } catch (e: CancellationException) {
            runCatching { File(safeOutputFile(fileName).path + ".part").delete() }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $modelId: ${e.message}", e)
            val retryable = when (e) {
                is DownloadFailure -> e.retryable
                is IOException -> true
                else -> false
            }
            if (retryable && runAttemptCount < 2) {
                Result.retry()
            } else {
                val code = (e as? DownloadFailure)?.errorCode ?: "download_failed"
                failure(code, e.message ?: "Error desconocido durante la descarga")
            }
        }
    }

    private suspend fun downloadWithResume(
        modelId: String,
        modelName: String,
        modelUrl: String,
        fileName: String,
        expectedSize: Long,
        exactSize: Boolean
    ) {
        val outputFile = safeOutputFile(fileName)
        val partialFile = File(outputFile.path + ".part")

        if (outputFile.exists()) {
            val complete = outputFile.length() > 0 &&
                (!exactSize || expectedSize <= 0 || outputFile.length() == expectedSize)
            if (complete) return
            outputFile.delete()
        }

        if (partialFile.exists() && exactSize && expectedSize > 0 && partialFile.length() > expectedSize) {
            partialFile.delete()
        }

        performDownload(
            modelName = modelName,
            modelUrl = modelUrl,
            partialFile = partialFile,
            outputFile = outputFile,
            expectedSize = expectedSize,
            exactSize = exactSize,
            allowRangeRestart = true
        )

        Log.d(TAG, "Download completed: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
    }

    private suspend fun performDownload(
        modelName: String,
        modelUrl: String,
        partialFile: File,
        outputFile: File,
        expectedSize: Long,
        exactSize: Boolean,
        allowRangeRestart: Boolean
    ) {
        var alreadyDownloaded = partialFile.takeIf { it.exists() }?.length() ?: 0L
        Log.d(TAG, "Starting/resuming model download at $alreadyDownloaded bytes")

        val requestBuilder = Request.Builder()
            .url(modelUrl)
            .header("User-Agent", "AIAgents-Android/${BuildConfig.VERSION_NAME}")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")

        if (alreadyDownloaded > 0) {
            requestBuilder.header("Range", "bytes=$alreadyDownloaded-")
        }

        securePreferences.getHuggingFaceToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Authorization", "Bearer $it") }

        val response = okHttpClient.newCall(requestBuilder.get().build()).execute()

        if (response.code == 416 && alreadyDownloaded > 0) {
            response.close()
            if (exactSize && expectedSize > 0 && alreadyDownloaded == expectedSize) {
                finalizePartialFile(partialFile, outputFile)
                return
            }
            partialFile.delete()
            if (allowRangeRestart) {
                performDownload(
                    modelName = modelName,
                    modelUrl = modelUrl,
                    partialFile = partialFile,
                    outputFile = outputFile,
                    expectedSize = expectedSize,
                    exactSize = exactSize,
                    allowRangeRestart = false
                )
                return
            }
            throw DownloadFailure("range_rejected", "El servidor rechazó la reanudación")
        }

        response.use { safeResponse ->
            if (!safeResponse.isSuccessful) {
                val code = safeResponse.code
                val (errorCode, message) = when (code) {
                    401 -> "invalid_token" to "El token de Hugging Face es inválido o expiró"
                    403 -> "access_denied" to "Acepta la licencia del modelo y revisa tu token"
                    404 -> "not_found" to "El modelo o archivo ya no existe"
                    408 -> "timeout" to "Hugging Face tardó demasiado en responder"
                    429 -> "rate_limited" to "Hugging Face limitó temporalmente las descargas"
                    in 500..599 -> "server_error" to "Hugging Face no está disponible temporalmente"
                    else -> "http_$code" to "Hugging Face respondió HTTP $code"
                }
                throw DownloadFailure(
                    errorCode = errorCode,
                    message = message,
                    retryable = code == 408 || code == 429 || code in 500..599
                )
            }

            val body = safeResponse.body
                ?: throw DownloadFailure("empty_response", "Hugging Face devolvió una respuesta vacía")
            val contentLength = body.contentLength()
            val isResuming = safeResponse.code == 206 && alreadyDownloaded > 0

            if (!isResuming) {
                alreadyDownloaded = 0L
            }

            val responseTotalSize = when {
                isResuming && contentLength > 0 -> alreadyDownloaded + contentLength
                contentLength > 0 -> contentLength
                exactSize && expectedSize > 0 -> expectedSize
                else -> 0L
            }
            val progressTotalSize = responseTotalSize.takeIf { it > 0 } ?: expectedSize
            val progressStep = if (progressTotalSize > 0) {
                (progressTotalSize / 20).coerceAtLeast(1L)
            } else {
                Long.MAX_VALUE
            }

            RandomAccessFile(partialFile, "rw").use { file ->
                if (isResuming) {
                    file.seek(alreadyDownloaded)
                } else {
                    file.setLength(0)
                }

                body.byteStream().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    var totalRead = alreadyDownloaded
                    var lastProgressUpdate = 0L

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        file.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.nanoTime()
                        if (now - lastProgressUpdate >= 500_000_000L ||
                            (progressTotalSize > 0 && totalRead % progressStep < bytesRead)
                        ) {
                            val progress = if (progressTotalSize > 0) {
                                (totalRead.toFloat() / progressTotalSize).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            setProgress(
                                workDataOf(
                                    PROGRESS_KEY to (progress * 100).toInt(),
                                    PROGRESS_BYTES_DOWNLOADED to totalRead,
                                    PROGRESS_BYTES_TOTAL to progressTotalSize
                                )
                            )
                            setForeground(createForegroundInfo(modelName, progress))
                            lastProgressUpdate = now
                        }
                    }
                }
            }

            val downloadedSize = partialFile.length()
            if (downloadedSize <= 0) {
                throw DownloadFailure("empty_file", "El archivo descargado está vacío")
            }
            if (responseTotalSize > 0 && downloadedSize != responseTotalSize) {
                throw DownloadFailure(
                    "incomplete_file",
                    "La descarga quedó incompleta ($downloadedSize de $responseTotalSize bytes)",
                    retryable = true
                )
            }
            if (exactSize && expectedSize > 0 && downloadedSize != expectedSize) {
                throw DownloadFailure(
                    "size_mismatch",
                    "El tamaño descargado no coincide con el publicado por Hugging Face"
                )
            }
        }

        finalizePartialFile(partialFile, outputFile)
    }

    private fun finalizePartialFile(partialFile: File, outputFile: File) {
        if (!partialFile.exists() || partialFile.length() <= 0) {
            throw DownloadFailure("missing_partial", "No existe una descarga completa para finalizar")
        }
        try {
            Files.move(
                partialFile.toPath(),
                outputFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                partialFile.toPath(),
                outputFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun safeOutputFile(fileName: String): File {
        val outputFile = File(modelsDir, fileName)
        val expectedParent = modelsDir.canonicalFile
        if (outputFile.canonicalFile.parentFile != expectedParent) {
            throw DownloadFailure("invalid_file_name", "El nombre local del modelo no es válido")
        }
        return outputFile
    }

    private fun failure(code: String, message: String): Result = Result.failure(
        workDataOf(
            OUTPUT_ERROR_CODE to code,
            OUTPUT_ERROR_MESSAGE to message
        )
    )

    private fun createForegroundInfo(modelName: String, progress: Float): ForegroundInfo {
        createNotificationChannel()

        val notificationId = NOTIFICATION_ID_BASE + Math.abs(id.hashCode()) % 10000
        val progressPercent = (progress.coerceIn(0f, 1f) * 100).toInt()
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Descargando modelo: $modelName")
            .setContentText("$progressPercent% completado")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Descargas de Modelos",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificaciones de descarga de modelos locales"
            setShowBadge(false)
        }

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
