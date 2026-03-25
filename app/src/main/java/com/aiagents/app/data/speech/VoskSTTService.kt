package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Local STT using Vosk for low-end devices (<4GB RAM).
 * Uses vosk-model-small-es (~50MB) for Spanish.
 * Lightweight, fast, works well on limited hardware.
 */
class VoskSTTService(
    context: Context,
    private val modelPath: String
) : BaseSTTService(context) {

    private var voskModel: Model? = null

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady = _isModelReady.asStateFlow()

    init {
        checkAndInitModel()
    }

    private fun checkAndInitModel() {
        val modelDir = File(modelPath)
        _isModelReady.value = modelDir.exists() && modelDir.isDirectory &&
                (modelDir.listFiles()?.isNotEmpty() == true)

        if (_isModelReady.value) {
            try {
                voskModel = Model(modelPath)
                Log.d(TAG, "Vosk model loaded from $modelPath")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Vosk model", e)
                _isModelReady.value = false
            }
        }
    }

    override suspend fun startListening(language: String) {
        if (!_isModelReady.value) {
            _transcription.value = "Error: Modelo Vosk no descargado. Descarga el modelo primero."
            return
        }

        _transcription.value = ""
        // Set early so ViewModel can observe isListening without race condition
        // (startRecording() also sets it, but the recording job runs asynchronously)
        _isListening.value = true

        recordingJob = serviceScope.launch {
            val audioData = startRecording()
            // startRecording() already cleaned up audioRecord and set _isListening = false
            if (audioData != null && audioData.isNotEmpty()) {
                val result = transcribeAudio(audioData)
                _transcription.value = result.getOrDefault("")
            }
        }
    }

    override suspend fun transcribeAudio(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = voskModel ?: run {
                voskModel = Model(modelPath)
                voskModel
            } ?: return@withContext Result.failure(IllegalStateException("No se pudo cargar modelo Vosk"))

            val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            // Feed audio in chunks of 4096 bytes
            val chunkSize = 4096
            var offset = 0
            while (offset < audioData.size) {
                val end = (offset + chunkSize).coerceAtMost(audioData.size)
                val chunk = audioData.copyOfRange(offset, end)
                recognizer.acceptWaveForm(chunk, chunk.size)
                offset = end
            }

            val resultJson = recognizer.finalResult
            recognizer.close()

            val text = parseVoskResult(resultJson)
            Log.d(TAG, "Transcripcion Vosk: $text")
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Error en transcripcion Vosk", e)
            Result.failure(e)
        }
    }

    private fun parseVoskResult(json: String): String {
        return try {
            JSONObject(json).optString("text", "")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Vosk result: $json", e)
            ""
        }
    }

    override fun release() {
        try {
            voskModel?.close()
            voskModel = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Vosk model", e)
        }
        super.release()
    }

    companion object {
        private const val TAG = "VoskSTT"

        fun getModelPath(context: Context): String {
            return getModelPath(context, "vosk-model-small-es")
        }

        fun getModelPath(context: Context, dirName: String): String {
            return File(context.filesDir, "vosk_models/$dirName").absolutePath
        }

        fun isModelDownloaded(context: Context): Boolean {
            return isModelDownloaded(context, "vosk-model-small-es")
        }

        fun isModelDownloaded(context: Context, dirName: String): Boolean {
            val dir = File(getModelPath(context, dirName))
            return dir.exists() && dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
        }

        fun getDownloadedModels(context: Context): Set<String> {
            return ModelDownloader.VOSK_MODELS
                .filter { isModelDownloaded(context, it.dirName) }
                .map { it.id }
                .toSet()
        }
    }
}
