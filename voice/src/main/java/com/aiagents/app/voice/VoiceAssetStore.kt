package com.aiagents.app.voice

import android.content.Context
import com.aiagents.app.data.speech.VoiceCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private enum class AssetLayout {
    WHISPER,
    PIPER
}

private data class VoiceAssetSpec(
    val id: String,
    val url: String,
    val sha256: String,
    val archiveBytes: Long,
    val layout: AssetLayout
)

/** Stores every speech model outside the APK and keeps interrupted downloads resumable. */
object VoiceAssetStore {
    private val specs = listOf(
        VoiceAssetSpec(
            id = VoiceCatalog.WHISPER_TINY_ID,
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
            sha256 = "c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1",
            archiveBytes = 116_204_861L,
            layout = AssetLayout.WHISPER
        ),
        VoiceAssetSpec(
            id = VoiceCatalog.PIPER_ALD_ID,
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-ald-medium-int8.tar.bz2",
            sha256 = "447e82d080719409db08e54a4b6eec2e4b6ba850b98dcaf09d3fc67bf30ff692",
            archiveBytes = 21_283_187L,
            layout = AssetLayout.PIPER
        ),
        VoiceAssetSpec(
            id = VoiceCatalog.PIPER_CLAUDE_ID,
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high-int8.tar.bz2",
            sha256 = "0f9fc9c07d2e17bdc0f5f33a657addae92085da85704cb86a861e59d32f3bbfa",
            archiveBytes = 21_216_685L,
            layout = AssetLayout.PIPER
        )
    ).associateBy(VoiceAssetSpec::id)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun assetDirectory(context: Context, assetId: String): File =
        File(context.filesDir, "voice_assets/$assetId")

    fun isReady(context: Context, assetId: String): Boolean {
        val spec = specs[assetId] ?: return false
        val directory = assetDirectory(context, assetId)
        return when (spec.layout) {
            AssetLayout.WHISPER ->
                File(directory, "encoder.onnx").length() >= 10_000_000L &&
                    File(directory, "decoder.onnx").length() >= 80_000_000L &&
                    File(directory, "tokens.txt").length() > 100_000L
            AssetLayout.PIPER ->
                File(directory, "model.onnx").length() >= 15_000_000L &&
                    File(directory, "tokens.txt").isFile &&
                    File(directory, "espeak-ng-data/phontab").isFile
        }
    }

    suspend fun download(
        context: Context,
        assetId: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val spec = specs[assetId]
            ?: return@withContext Result.failure(IllegalArgumentException("Modelo de voz desconocido"))
        if (isReady(context, assetId)) {
            onProgress(1f)
            return@withContext Result.success(Unit)
        }

        try {
            val downloadDirectory = File(context.cacheDir, "voice_downloads").apply { mkdirs() }
            val archive = File(downloadDirectory, "$assetId.tar.bz2.part")
            val staging = File(context.filesDir, "voice_assets/$assetId.staging")
            staging.deleteRecursively()
            staging.mkdirs()

            downloadArchive(spec, archive, onProgress)
            check(archive.sha256() == spec.sha256) {
                archive.delete()
                "La verificacion del modelo fallo; vuelve a descargarlo"
            }
            extract(spec, archive, staging, onProgress)
            check(isReadyAt(staging, spec.layout)) { "El modelo descargado esta incompleto" }

            val destination = assetDirectory(context, assetId)
            destination.parentFile?.mkdirs()
            destination.deleteRecursively()
            check(staging.renameTo(destination)) { "No se pudo activar el modelo de voz" }
            archive.delete()
            onProgress(1f)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            File(context.filesDir, "voice_assets/$assetId.staging").deleteRecursively()
            Result.failure(error)
        }
    }

    fun delete(context: Context, assetId: String): Result<Unit> = runCatching {
        check(specs.containsKey(assetId)) { "Modelo de voz desconocido" }
        assetDirectory(context, assetId).deleteRecursively()
        File(context.filesDir, "voice_assets/$assetId.staging").deleteRecursively()
        File(context.cacheDir, "voice_downloads/$assetId.tar.bz2.part").delete()
    }

    private fun downloadArchive(
        spec: VoiceAssetSpec,
        output: File,
        onProgress: (Float) -> Unit
    ) {
        if (output.length() > spec.archiveBytes) output.delete()
        if (output.length() == spec.archiveBytes) {
            onProgress(DOWNLOAD_WEIGHT)
            return
        }
        val existingBytes = output.length()
        val request = Request.Builder()
            .url(spec.url)
            .apply { if (existingBytes > 0L) header("Range", "bytes=$existingBytes-") }
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} al descargar el modelo" }
            val body = checkNotNull(response.body) { "Respuesta de modelo vacia" }
            val append = existingBytes > 0L && response.code == 206
            val startingBytes = if (append) existingBytes else 0L
            body.byteStream().use { input ->
                FileOutputStream(output, append).use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = startingBytes
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        stream.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            (downloaded.toFloat() / spec.archiveBytes.toFloat() * DOWNLOAD_WEIGHT)
                                .coerceIn(0f, DOWNLOAD_WEIGHT)
                        )
                    }
                }
            }
        }
        check(output.length() == spec.archiveBytes) {
            "La descarga quedo incompleta (${output.length()} de ${spec.archiveBytes} bytes)"
        }
    }

    private fun extract(
        spec: VoiceAssetSpec,
        archive: File,
        destination: File,
        onProgress: (Float) -> Unit
    ) {
        var extractedBytes = 0L
        val expectedBytes = VoiceCatalog.find(spec.id)?.installedBytes?.coerceAtLeast(1L) ?: 1L
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val target = entry.targetFile(spec.layout, destination)
                if (target != null && entry.isSafeRegularFile()) {
                    target.parentFile?.mkdirs()
                    check(target.canonicalPath.startsWith(destination.canonicalPath + File.separator)) {
                        "Ruta invalida dentro del paquete de voz"
                    }
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                    extractedBytes += target.length()
                    val extractionProgress =
                        (extractedBytes.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f)
                    onProgress(DOWNLOAD_WEIGHT + extractionProgress * (1f - DOWNLOAD_WEIGHT))
                }
                entry = input.nextEntry
            }
        }
    }

    private fun TarArchiveEntry.targetFile(layout: AssetLayout, destination: File): File? {
        val relative = name.substringAfter('/', missingDelimiterValue = name)
        return when (layout) {
            AssetLayout.WHISPER -> when {
                relative == "tiny-encoder.int8.onnx" -> File(destination, "encoder.onnx")
                relative == "tiny-decoder.int8.onnx" -> File(destination, "decoder.onnx")
                relative == "tiny-tokens.txt" -> File(destination, "tokens.txt")
                else -> null
            }
            AssetLayout.PIPER -> when {
                relative.endsWith(".onnx") -> File(destination, "model.onnx")
                relative == "tokens.txt" -> File(destination, "tokens.txt")
                relative.startsWith("espeak-ng-data/") -> File(destination, relative)
                relative == "MODEL_CARD" -> File(destination, relative)
                else -> null
            }
        }
    }

    private fun TarArchiveEntry.isSafeRegularFile(): Boolean =
        !isDirectory && !isSymbolicLink && !isLink

    private fun isReadyAt(directory: File, layout: AssetLayout): Boolean = when (layout) {
        AssetLayout.WHISPER ->
            File(directory, "encoder.onnx").length() >= 10_000_000L &&
                File(directory, "decoder.onnx").length() >= 80_000_000L &&
                File(directory, "tokens.txt").length() > 100_000L
        AssetLayout.PIPER ->
            File(directory, "model.onnx").length() >= 15_000_000L &&
                File(directory, "tokens.txt").isFile &&
                File(directory, "espeak-ng-data/phontab").isFile
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val DOWNLOAD_WEIGHT = 0.9f
}
