package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object ModelDownloader {

    private const val TAG = "ModelDownloader"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Models from GitHub Releases (public, no auth required)
    private val SHERPA_MODELS = mapOf(
        "whisper-tiny" to SherpaModelInfo(
            tarballUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            totalSize = 39_000_000L
        ),
        "whisper-base" to SherpaModelInfo(
            tarballUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2",
            totalSize = 74_000_000L
        ),
        "whisper-small" to SherpaModelInfo(
            tarballUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2",
            totalSize = 244_000_000L
        )
    )

    // Vosk model catalog — multiple sizes for different accuracy/size tradeoffs
    data class VoskModelInfo(
        val id: String,
        val displayName: String,
        val url: String,
        val size: Long,
        val description: String,
        val dirName: String // extracted directory name inside vosk_models/
    )

    val VOSK_MODELS = listOf(
        VoskModelInfo(
            id = "vosk-small-es",
            displayName = "Espanol (Small)",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            size = 50_000_000L,
            description = "~50 MB - Rapido, precision basica",
            dirName = "vosk-model-small-es"
        ),
        VoskModelInfo(
            id = "vosk-es",
            displayName = "Espanol (Large)",
            url = "https://alphacephei.com/vosk/models/vosk-model-es-0.42.zip",
            size = 1_400_000_000L,
            description = "~1.4 GB - Alta precision",
            dirName = "vosk-model-es"
        ),
        VoskModelInfo(
            id = "vosk-small-en",
            displayName = "English (Small)",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            size = 40_000_000L,
            description = "~40 MB - Fast, basic accuracy",
            dirName = "vosk-model-small-en-us"
        ),
        VoskModelInfo(
            id = "vosk-en",
            displayName = "English (Large)",
            url = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
            size = 1_800_000_000L,
            description = "~1.8 GB - High accuracy",
            dirName = "vosk-model-en-us"
        )
    )

    fun getVoskModelInfo(modelId: String): VoskModelInfo? =
        VOSK_MODELS.find { it.id == modelId }

    suspend fun downloadSherpaOnnxModel(
        context: Context,
        modelName: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val info = SHERPA_MODELS[modelName]
                ?: return@withContext Result.failure(IllegalArgumentException("Modelo desconocido: $modelName"))

            val modelDir = File(context.filesDir, "sherpa_models/$modelName")
            modelDir.mkdirs()

            val tempTar = File(context.cacheDir, "$modelName.tar.bz2")

            // Download tarball (70% of progress)
            Log.d(TAG, "Descargando tarball de $modelName desde GitHub releases...")
            downloadFile(info.tarballUrl, tempTar) { bytes ->
                onProgress((bytes.toFloat() / info.totalSize * 0.7f).coerceAtMost(0.7f))
            }

            // Extract encoder.onnx, decoder.onnx, tokens.txt (30% of progress)
            Log.d(TAG, "Extrayendo modelo $modelName...")
            extractTarBz2(tempTar, modelDir) { progress ->
                onProgress(0.7f + progress * 0.3f)
            }

            tempTar.delete()
            onProgress(1f)
            Log.d(TAG, "Modelo sherpa-onnx '$modelName' listo en: ${modelDir.absolutePath}")
            Result.success(modelDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo sherpa-onnx", e)
            Result.failure(e)
        }
    }

    suspend fun downloadVoskModel(
        context: Context,
        onProgress: (Float) -> Unit
    ): Result<File> = downloadVoskModel(context, "vosk-small-es", onProgress)

    suspend fun downloadVoskModel(
        context: Context,
        modelId: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelInfo = getVoskModelInfo(modelId)
                ?: return@withContext Result.failure(IllegalArgumentException("Unknown Vosk model: $modelId"))

            val modelsDir = File(context.filesDir, "vosk_models")
            modelsDir.mkdirs()

            val tempZip = File(context.cacheDir, "vosk_model_temp_${modelId}.zip")

            Log.d(TAG, "Descargando modelo Vosk: $modelId...")
            downloadFile(modelInfo.url, tempZip) { bytes ->
                onProgress(bytes.toFloat() / modelInfo.size * 0.7f)
            }

            Log.d(TAG, "Extrayendo modelo Vosk: $modelId...")
            val outputDir = File(modelsDir, modelInfo.dirName)
            extractZip(tempZip, modelsDir) { progress ->
                onProgress(0.7f + progress * 0.3f)
            }

            tempZip.delete()

            // The ZIP root dir may include version suffix. Rename to canonical name.
            if (!outputDir.exists()) {
                val extracted = modelsDir.listFiles()?.firstOrNull {
                    it.isDirectory && it.name.startsWith(modelInfo.dirName)
                }
                if (extracted != null) {
                    extracted.renameTo(outputDir)
                    Log.d(TAG, "Renombrado '${extracted.name}' -> '${modelInfo.dirName}'")
                }
            }

            onProgress(1f)
            Log.d(TAG, "Modelo Vosk descargado en: ${outputDir.absolutePath}")
            Result.success(outputDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo Vosk: $modelId", e)
            Result.failure(e)
        }
    }

    private fun downloadFileWithProgress(
        urlString: String,
        outputFile: File,
        totalSize: Long,
        onProgress: (Float) -> Unit
    ) {
        val request = Request.Builder().url(urlString).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code} para $urlString")
        }

        val body = response.body ?: throw RuntimeException("Respuesta vacía para $urlString")
        val contentLength = body.contentLength().takeIf { it > 0 } ?: totalSize

        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress((downloaded.toFloat() / contentLength).coerceAtMost(1f))
                }
            }
        }
    }

    private fun downloadFile(
        urlString: String,
        outputFile: File,
        onBytesDownloaded: (Long) -> Unit
    ) {
        val request = Request.Builder().url(urlString).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code} para $urlString")
        }

        val body = response.body ?: throw RuntimeException("Respuesta vacía para $urlString")
        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    onBytesDownloaded(downloaded)
                }
            }
        }
    }

    /**
     * Extracts encoder.onnx, decoder.onnx, and tokens.txt from a sherpa-onnx .tar.bz2 archive.
     * The tarball may use naming like "tiny-encoder.int8.onnx"; we map by keyword to the
     * canonical names expected by SherpaOnnxSTTService.
     */
    private fun extractTarBz2(tarFile: File, destDir: File, onProgress: (Float) -> Unit) {
        val tarSize = tarFile.length().coerceAtLeast(1L)
        var processed = 0L

        TarArchiveInputStream(
            BZip2CompressorInputStream(tarFile.inputStream().buffered())
        ).use { tis ->
            var entry = tis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = File(entry.name).name  // strip directory prefix

                    val targetFileName = when {
                        entryName.contains("encoder") && entryName.endsWith(".onnx") -> "encoder.onnx"
                        entryName.contains("decoder") && entryName.endsWith(".onnx") -> "decoder.onnx"
                        entryName.contains("tokens") && entryName.endsWith(".txt") -> "tokens.txt"
                        else -> null
                    }

                    if (targetFileName != null) {
                        val outFile = File(destDir, targetFileName)
                        FileOutputStream(outFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (tis.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                                processed += read
                                onProgress((processed.toFloat() / tarSize).coerceAtMost(1f))
                            }
                        }
                        Log.d(TAG, "Extraído: ${entry.name} → $targetFileName")
                    }
                }
                entry = tis.nextEntry
            }
        }
    }

    private fun extractZip(zipFile: File, destDir: File, onProgress: (Float) -> Unit) {
        val zipSize = zipFile.length()
        var extracted = 0L

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)

                // Security: prevent zip slip
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw SecurityException("Zip entry outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            extracted += read
                            onProgress((extracted.toFloat() / zipSize).coerceAtMost(1f))
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    data class SherpaModelInfo(
        val tarballUrl: String,
        val totalSize: Long
    )
}
