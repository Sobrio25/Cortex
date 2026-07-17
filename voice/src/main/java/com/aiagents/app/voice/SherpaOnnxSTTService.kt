package com.aiagents.app.voice

import android.content.Context
import android.util.Log
import com.aiagents.app.data.speech.BaseSTTService
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline Whisper/STT implementation, intentionally absent from the base APK. */
class SherpaOnnxSTTService(
    context: Context,
    private val modelDir: String,
    private val language: String,
    private val useNnapi: Boolean = true
) : BaseSTTService(context) {
    private var recognizer: OfflineRecognizer? = null
    private val _isModelReady = MutableStateFlow(false)
    val isModelReady = _isModelReady.asStateFlow()

    init {
        _isModelReady.value = isWhisperModelReady(File(modelDir))
        if (_isModelReady.value) runCatching(::initializeRecognizer)
            .onFailure {
                Log.e(TAG, "Unable to initialize offline recognizer", it)
                _isModelReady.value = false
            }
    }

    override suspend fun startListening(language: String) {
        if (!_isModelReady.value) {
            _transcription.value = "Error: El modelo de voz sin conexión no está listo"
            return
        }
        startRecordingSession { audio ->
            _transcription.value = transcribeAudio(audio).getOrDefault("")
        }
    }

    override suspend fun transcribeAudio(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val activeRecognizer = recognizer ?: initializeRecognizer().also { recognizer = it }
            val samples = pcmBytesToFloatArray(audioData)
            val stream = activeRecognizer.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            activeRecognizer.decode(stream)
            activeRecognizer.getResult(stream).text.trim()
        }.onFailure { Log.e(TAG, "Offline transcription failed", it) }
    }

    private fun initializeRecognizer(): OfflineRecognizer {
        val model = OfflineWhisperModelConfig(
            encoder = File(modelDir, "encoder.onnx").absolutePath,
            decoder = File(modelDir, "decoder.onnx").absolutePath,
            language = language.substringBefore('-'),
            task = "transcribe"
        )
        return OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = model,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    provider = if (useNnapi) "nnapi" else "cpu",
                    modelType = "whisper"
                )
            )
        )
    }

    private fun pcmBytesToFloatArray(pcmData: ByteArray): FloatArray {
        val shorts = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get(it).toFloat() / Short.MAX_VALUE }
    }

    override fun release() {
        recognizer = null
        super.release()
    }

    private companion object {
        const val TAG = "SherpaOnnxSTT"

        fun isWhisperModelReady(directory: File): Boolean =
            File(directory, "encoder.onnx").length() >= 10_000_000L &&
                File(directory, "decoder.onnx").length() >= 80_000_000L &&
                File(directory, "tokens.txt").length() > 100_000L
    }
}
