package com.aiagents.app.data.knowledge

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Downloads the MediaPipe Text Embedder model (Universal Sentence Encoder,
 * ~27 MB, float32) from Google's model storage into filesDir/models/.
 *
 * Exposes download progress as a StateFlow<Float?> (null = idle, 0f..1f = in
 * progress, 1f = done). Idempotent: skips the download when the file exists.
 */
@Singleton
class EmbeddingModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embedderManager: TextEmbedderManager
) {
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    fun isDownloaded(): Boolean = embedderManager.isModelReady()

    suspend fun downloadIfNeeded(): Result<Unit> = withContext(Dispatchers.IO) {
        if (embedderManager.isModelReady()) return@withContext Result.success(Unit)
        runCatching {
            val target = embedderManager.modelFile()
            target.parentFile?.mkdirs()
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("Download failed: HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                val input = connection.inputStream
                val output = FileOutputStream(target)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                var received = 0L
                input.use { source ->
                    output.use { dest ->
                        while (source.read(buffer).also { read = it } != -1) {
                            dest.write(buffer, 0, read)
                            received += read
                            if (total > 0) _progress.value = (received.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
                _progress.value = 1f
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite"
    }
}
