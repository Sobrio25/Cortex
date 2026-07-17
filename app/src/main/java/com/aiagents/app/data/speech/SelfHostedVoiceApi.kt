package com.aiagents.app.data.speech

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** OpenAI-compatible speech endpoints hosted on infrastructure chosen by the user. */
@Singleton
class SelfHostedVoiceApi @Inject constructor(
    private val client: OkHttpClient
) {
    private val voiceClient = client.newBuilder()
        // A LAN endpoint that is offline or blocked by a firewall must fail quickly. Model
        // inference may still take several minutes after the connection has been established.
        .connectTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        audioFile: File,
        config: RemoteSttConfig,
        language: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching<String> {
            requireValidEndpoint(config.endpointUrl)
            require(config.model.isNotBlank()) { "Especifica el modelo Whisper del servidor" }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", config.model.trim())
                .apply {
                    val languageCode = language.substringBefore('-').trim().lowercase()
                    if (languageCode.isNotBlank() && languageCode != "auto") {
                        addFormDataPart("language", languageCode)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(config.endpointUrl.trim())
                .optionalBearer(config.apiKey)
                .header("Accept", "application/json")
                .post(body)
                .build()

            Log.d(TAG, "STT request ${safeEndpoint(config.endpointUrl)} model=${config.model.trim()}")

            voiceClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "STT response status=${response.code}")
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "El servidor STT respondio ${response.code}: ${responseBody.safeErrorBody()}"
                    )
                }
                val text = runCatching { JSONObject(responseBody).optString("text") }
                    .getOrDefault("")
                    .ifBlank { responseBody.takeUnless { it.trimStart().startsWith("{") }.orEmpty() }
                    .trim()
                check(text.isNotBlank()) { "El servidor STT devolvio una transcripcion vacia" }
                text
            }
        }.mapFailureForLocalServer("STT")
    }

    suspend fun synthesize(
        text: String,
        config: RemoteTtsConfig
    ): Result<RemoteTtsAudio> = withContext(Dispatchers.IO) {
        runCatching<RemoteTtsAudio> {
            requireValidEndpoint(config.endpointUrl)
            require(config.model.isNotBlank()) { "Especifica el modelo TTS del servidor" }
            require(!Qwen3TtsVoiceSkill.requiresVoice(config) || config.voice.isNotBlank()) {
                "Especifica la voz del modelo TTS"
            }

            val json = JSONObject().apply {
                Qwen3TtsVoiceSkill.requestFields(text, config).forEach(::put)
            }
            val request = Request.Builder()
                .url(config.endpointUrl.trim())
                .optionalBearer(config.apiKey)
                .header("Accept", "audio/wav, audio/mpeg, audio/*")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(
                TAG,
                "TTS request ${safeEndpoint(config.endpointUrl)} model=${config.model.trim()} " +
                    "flavor=${Qwen3TtsVoiceSkill.resolveApiFlavor(config).id} " +
                    "adaptiveStyle=${config.adaptiveStyle}"
            )

            voiceClient.newCall(request).execute().use { response ->
                val bytes: ByteArray = response.body?.bytes() ?: ByteArray(0)
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                Log.d(
                    TAG,
                    "TTS response status=${response.code} type=${contentType.ifBlank { "unknown" }} bytes=${bytes.size}"
                )
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "El servidor TTS respondio ${response.code}: " +
                            bytes.decodeToString().safeErrorBody()
                    )
                }
                check(!contentType.contains("json") && !contentType.startsWith("text/")) {
                    "El servidor TTS devolvio texto en vez de audio: " +
                        bytes.decodeToString().safeErrorBody()
                }
                check(bytes.isNotEmpty()) { "El servidor TTS devolvio audio vacio" }
                normalizeTtsAudio(bytes, contentType)
            }
        }.mapFailureForLocalServer("TTS")
    }

    /** Reads headerless mono PCM16 as it arrives instead of buffering the full response. */
    suspend fun streamPcm(
        text: String,
        config: RemoteTtsConfig,
        onChunk: (ByteArray) -> Unit
    ): Result<RemotePcmStreamResult> = withContext(Dispatchers.IO) {
        runCatching<RemotePcmStreamResult> {
            requireValidEndpoint(config.endpointUrl)
            require(config.audioMode == RemoteTtsAudioMode.STREAMING_PCM) {
                "El modo de audio no es PCM streaming"
            }
            require(config.model.isNotBlank()) { "Especifica el modelo TTS del servidor" }
            require(!Qwen3TtsVoiceSkill.requiresVoice(config) || config.voice.isNotBlank()) {
                "Especifica la voz del modelo TTS"
            }
            require(config.pcmSampleRate in 8_000..96_000) {
                "La frecuencia PCM debe estar entre 8000 y 96000 Hz"
            }

            val json = JSONObject().apply {
                Qwen3TtsVoiceSkill.requestFields(text, config).forEach(::put)
            }
            val request = Request.Builder()
                .url(config.endpointUrl.trim())
                .optionalBearer(config.apiKey)
                .header("Accept", "audio/pcm, application/octet-stream")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(
                TAG,
                "TTS PCM stream request ${safeEndpoint(config.endpointUrl)} " +
                    "model=${config.model.trim()} sampleRate=${config.pcmSampleRate}"
            )

            val startedAt = System.nanoTime()
            voiceClient.newCall(request).execute().use { response ->
                val body = response.body ?: error("El servidor TTS devolvio una respuesta vacia")
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "El servidor TTS respondio ${response.code}: ${body.string().safeErrorBody()}"
                    )
                }
                check(contentType.contains("pcm") || contentType.contains("octet-stream")) {
                    "El servidor no devolvio PCM streaming (Content-Type: " +
                        contentType.ifBlank { "desconocido" } + ")"
                }

                val input = body.byteStream()
                val buffer = ByteArray(16 * 1024)
                var totalBytes = 0L
                var firstChunkLogged = false
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (!firstChunkLogged) {
                        val firstChunkMs = (System.nanoTime() - startedAt) / 1_000_000L
                        Log.d(TAG, "TTS PCM first chunk after ${firstChunkMs}ms bytes=$count")
                        firstChunkLogged = true
                    }
                    onChunk(buffer.copyOf(count))
                    totalBytes += count
                }
                check(totalBytes > 0L) { "El servidor TTS devolvio un flujo PCM vacio" }
                Log.d(TAG, "TTS PCM stream completed status=${response.code} bytes=$totalBytes")
                RemotePcmStreamResult(totalBytes)
            }
        }.mapFailureForLocalServer("TTS")
    }

    private fun Request.Builder.optionalBearer(apiKey: String): Request.Builder = apply {
        apiKey.trim().takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
    }

    private fun requireValidEndpoint(endpointUrl: String) {
        val error = endpointValidationError(endpointUrl)
        require(error == null) { error.orEmpty() }
    }

    private fun <T> Result<T>.mapFailureForLocalServer(kind: String): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { error ->
            val message = when {
                error.message?.contains("CLEARTEXT", ignoreCase = true) == true ->
                    "Android bloqueo la conexion HTTP. Usa HTTPS para el servidor $kind."
                error.message?.contains("Failed to connect", ignoreCase = true) == true ||
                    error.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "No se pudo conectar al servidor $kind. Revisa la IP, el puerto y que acepte conexiones de la red local."
                else -> error.message ?: "Fallo el servidor de voz $kind"
            }
            Log.w(TAG, "$kind request failed: $message")
            Result.failure(IllegalStateException(message, error))
        }
    )

    private fun String.safeErrorBody(): String =
        trim().replace(Regex("\\s+"), " ").take(300).ifBlank { "sin detalles" }

    companion object {
        private const val TAG = "SelfHostedVoiceApi"

        fun endpointValidationError(endpointUrl: String): String? {
            if (endpointUrl.isBlank()) return "Especifica la URL completa del endpoint"
            val url = endpointUrl.trim().toHttpUrlOrNull()
                ?: return "La URL del endpoint no es valida"
            if (url.scheme != "http" && url.scheme != "https") {
                return "La URL debe usar http o https"
            }
            return null
        }

        fun isConfigured(config: RemoteSttConfig): Boolean =
            endpointValidationError(config.endpointUrl) == null && config.model.isNotBlank()

        fun isConfigured(config: RemoteTtsConfig): Boolean =
            endpointValidationError(config.endpointUrl) == null &&
                config.model.isNotBlank() &&
                (!Qwen3TtsVoiceSkill.requiresVoice(config) || config.voice.isNotBlank()) &&
                (config.audioMode != RemoteTtsAudioMode.STREAMING_PCM ||
                    config.pcmSampleRate in 8_000..96_000)

        internal fun normalizeTtsAudio(bytes: ByteArray, contentType: String): RemoteTtsAudio {
            val isWav = contentType.contains("wav") ||
                bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                bytes.copyOfRange(8, 12).decodeToString() == "WAVE"
            return if (isWav) {
                RemoteTtsAudio(repairStreamingWavHeader(bytes), ".wav")
            } else {
                RemoteTtsAudio(bytes, ".mp3")
            }
        }

        private fun repairStreamingWavHeader(source: ByteArray): ByteArray {
            if (source.size < 12) return source
            val result = source.copyOf()
            writeLittleEndianInt(result, 4, result.size - 8)

            val dataOffset = (12..result.size - 8).firstOrNull { index ->
                result[index] == 'd'.code.toByte() &&
                    result[index + 1] == 'a'.code.toByte() &&
                    result[index + 2] == 't'.code.toByte() &&
                    result[index + 3] == 'a'.code.toByte()
            }
            if (dataOffset != null) {
                writeLittleEndianInt(result, dataOffset + 4, result.size - dataOffset - 8)
            }
            return result
        }

        private fun writeLittleEndianInt(target: ByteArray, offset: Int, value: Int) {
            if (offset < 0 || offset + 4 > target.size) return
            target[offset] = (value and 0xff).toByte()
            target[offset + 1] = (value ushr 8 and 0xff).toByte()
            target[offset + 2] = (value ushr 16 and 0xff).toByte()
            target[offset + 3] = (value ushr 24 and 0xff).toByte()
        }

        private fun safeEndpoint(endpointUrl: String): String =
            endpointUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}:${it.port}${it.encodedPath}" }
                ?: "invalid-endpoint"
    }
}
