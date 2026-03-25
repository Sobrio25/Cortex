package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Local STT using sherpa-onnx with Whisper ONNX models.
 * Supports NNAPI for hardware acceleration on Snapdragon NPU and Google Tensor.
 * Requires encoder.onnx, decoder.onnx, and tokens.txt in the model directory.
 */
class SherpaOnnxSTTService(
    context: Context,
    private val modelDir: String,
    private val language: String = "es",
    private val useNNAPI: Boolean = true
) : BaseSTTService(context) {

    private var recognizer: OfflineRecognizer? = null

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady = _isModelReady.asStateFlow()

    init {
        checkAndInitModel()
    }

    private fun checkAndInitModel() {
        val dir = File(modelDir)
        val encoderFile = File(dir, "encoder.onnx")
        val decoderFile = File(dir, "decoder.onnx")
        val tokensFile = File(dir, "tokens.txt")

        val filesExist = encoderFile.exists() && decoderFile.exists() && tokensFile.exists()

        if (filesExist) {
            // Whisper encoder ONNX models are large (>5MB minimum).
            // A file smaller than this is corrupt/invalid and would cause a native SIGABRT crash
            // that cannot be caught from Kotlin, so we must reject it here.
            val encoderSize = encoderFile.length()
            val decoderSize = decoderFile.length()
            if (encoderSize < MIN_VALID_MODEL_BYTES || decoderSize < MIN_VALID_MODEL_BYTES) {
                Log.e(TAG, "Archivos de modelo inválidos (encoder=${encoderSize}B, decoder=${decoderSize}B). " +
                        "Mínimo esperado: ${MIN_VALID_MODEL_BYTES}B. Eliminando para re-descarga.")
                encoderFile.delete()
                decoderFile.delete()
                tokensFile.delete()
                _isModelReady.value = false
                return
            }
        }

        _isModelReady.value = filesExist

        if (_isModelReady.value) {
            try {
                initRecognizer()
            } catch (e: Exception) {
                Log.e(TAG, "Error inicializando recognizer", e)
                _isModelReady.value = false
            }
        }
    }

    private fun initRecognizer() {
        val whisperConfig = OfflineWhisperModelConfig(
            encoder = File(modelDir, "encoder.onnx").absolutePath,
            decoder = File(modelDir, "decoder.onnx").absolutePath,
            language = language,
            task = "transcribe"
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = File(modelDir, "tokens.txt").absolutePath,
            numThreads = 2,
            provider = if (useNNAPI) "nnapi" else "cpu",
            modelType = "whisper"
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig
        )

        recognizer = OfflineRecognizer(config = config)
        Log.d(TAG, "Recognizer inicializado (provider=${if (useNNAPI) "nnapi" else "cpu"})")
    }

    override suspend fun startListening(language: String) {
        if (!_isModelReady.value) {
            _transcription.value = "Error: Modelo no descargado. Descarga el modelo primero."
            return
        }

        recordingJob = serviceScope.launch {
            val audioData = startRecording()
            if (audioData != null) {
                val result = transcribeAudio(audioData)
                _transcription.value = result.getOrDefault("")
            }
        }
    }

    override suspend fun transcribeAudio(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rec = recognizer ?: run {
                initRecognizer()
                recognizer
            } ?: return@withContext Result.failure(IllegalStateException("No se pudo inicializar recognizer"))

            val floatSamples = pcmBytesToFloatArray(audioData)

            val stream = rec.createStream()
            stream.acceptWaveform(floatSamples, sampleRate = SAMPLE_RATE)
            rec.decode(stream)
            val text = rec.getResult(stream).text.trim()

            Log.d(TAG, "Transcripcion: $text")
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Error en transcripcion", e)
            Result.failure(e)
        }
    }

    private fun pcmBytesToFloatArray(pcmData: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcmData)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val shorts = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shorts)
        return FloatArray(shorts.size) { shorts[it].toFloat() / Short.MAX_VALUE }
    }

    override fun release() {
        try {
            recognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando recognizer", e)
        }
        super.release()
    }

    companion object {
        private const val TAG = "SherpaOnnxSTT"
        private const val MIN_VALID_MODEL_BYTES = 5_000_000L  // 5MB — any real Whisper ONNX is much larger

        fun getModelDir(context: Context, modelName: String): String {
            return File(context.filesDir, "sherpa_models/$modelName").absolutePath
        }

        fun isModelDownloaded(context: Context, modelName: String): Boolean {
            val dir = File(getModelDir(context, modelName))
            return dir.exists() &&
                    File(dir, "encoder.onnx").exists() &&
                    File(dir, "decoder.onnx").exists() &&
                    File(dir, "tokens.txt").exists()
        }
    }
}
