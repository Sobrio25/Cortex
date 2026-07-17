package com.aiagents.app.data.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.aiagents.app.domain.service.STTService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

abstract class BaseSTTService(protected val context: Context) : STTService {

    protected val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    protected val _transcription = MutableStateFlow("")
    override val transcription: StateFlow<String> = _transcription.asStateFlow()

    private var recordingJob: Job? = null
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recordingSessionMutex = Mutex()

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_MULTIPLIER = 2

        private const val NON_BLOCKING_READ_POLL_MS = 10L
        private const val ANALYSIS_FRAME_MS = 100
    }

    /**
     * Starts at most one microphone/transcription session and does not return until AudioRecord is
     * actually recording (or startup fails). Keeping job ownership here prevents subclasses from
     * replacing the job while a previous AudioRecord is still alive.
     */
    protected suspend fun startRecordingSession(
        onAudioCaptured: suspend (ByteArray) -> Unit
    ): Boolean = recordingSessionMutex.withLock {
        val previousJob = recordingJob
        if (previousJob?.isActive == true) {
            if (_isListening.value) return@withLock true
            previousJob.join()
        }
        if (!serviceScope.isActive) return@withLock false

        val started = CompletableDeferred<Boolean>()
        recordingJob = serviceScope.launch {
            try {
                startRecording(started)
                    ?.takeIf(ByteArray::isNotEmpty)
                    ?.let { onAudioCaptured(it) }
            } finally {
                started.complete(false)
            }
        }
        started.await()
    }

    private suspend fun startRecording(started: CompletableDeferred<Boolean>): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("BaseSTTService", "Permiso de micrófono no concedido")
            started.complete(false)
            return null
        }
        val audioRecordBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
            .coerceAtLeast(SAMPLE_RATE * BUFFER_SIZE_MULTIPLIER)

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                audioRecordBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("BaseSTTService", "AudioRecord no se pudo inicializar")
                started.complete(false)
                return null
            }

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e("BaseSTTService", "AudioRecord no entro en estado de grabacion")
                started.complete(false)
                return null
            }
            _isListening.value = true
            started.complete(true)

            val outputStream = ByteArrayOutputStream()
            // Read in short frames even though AudioRecord owns a larger internal buffer. This
            // gives endpoint detection enough temporal resolution to notice natural pauses.
            val buffer = ByteArray(SAMPLE_RATE * 2 * ANALYSIS_FRAME_MS / 1_000)
            val voiceActivityDetector = AdaptiveVoiceActivityDetector(SAMPLE_RATE)
            var lastLoggedSecond = -1

            while (_isListening.value && currentCoroutineContext().isActive) {
                val read = record.read(
                    buffer,
                    0,
                    buffer.size,
                    AudioRecord.READ_NON_BLOCKING
                )
                when {
                    read > 0 -> {
                        outputStream.write(buffer, 0, read)
                        val samplesRead = read / 2 // 16-bit = 2 bytes per sample
                        val rms = calculateRMS(buffer, read)
                        val vad = voiceActivityDetector.accept(rms, samplesRead)
                        val elapsedSecond = vad.elapsedSeconds.toInt()
                        if (elapsedSecond != lastLoggedSecond) {
                            lastLoggedSecond = elapsedSecond
                            Log.v(
                                "BaseSTTService",
                                "VAD rms=${rms.toInt()} floor=${vad.noiseFloorRms.toInt()} " +
                                    "speech=${vad.speechThresholdRms.toInt()} " +
                                    "silence=${vad.silenceThresholdRms.toInt()} " +
                                    "spoken=${vad.hasSpoken}"
                            )
                        }
                        if (vad.shouldStop) {
                            val reason = when (vad.stopReason) {
                                VoiceStopReason.SILENCE_AFTER_SPEECH -> "silencio detectado tras habla"
                                VoiceStopReason.NO_SPEECH -> "sin habla detectada"
                                VoiceStopReason.MAX_DURATION -> "duracion maxima alcanzada"
                                null -> "fin de captura"
                            }
                            Log.d("BaseSTTService", "Auto-stop: $reason")
                            break
                        }
                    }
                    read == AudioRecord.ERROR_DEAD_OBJECT -> {
                        throw IllegalStateException("AudioRecord perdio el dispositivo de audio")
                    }
                    read < 0 -> {
                        throw IllegalStateException("AudioRecord.read fallo con codigo $read")
                    }
                    else -> delay(NON_BLOCKING_READ_POLL_MS)
                }
            }

            return outputStream.toByteArray()

        } catch (e: Exception) {
            Log.e("BaseSTTService", "Error grabando audio", e)
            return null
        } finally {
            started.complete(false)
            _isListening.value = false
            record?.let(::stopAndReleaseOwnedRecord)
        }
    }

    /** Called only by the coroutine that owns and reads this AudioRecord instance. */
    private fun stopAndReleaseOwnedRecord(record: AudioRecord) {
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (error: IllegalStateException) {
            Log.w("BaseSTTService", "AudioRecord ya estaba detenido")
        } finally {
            runCatching { record.release() }
                .onFailure { Log.w("BaseSTTService", "No se pudo liberar AudioRecord", it) }
        }
    }

    /**
     * Calculates the RMS (Root Mean Square) amplitude of a PCM 16-bit audio buffer.
     * Used for silence detection: values ~0-200 = silence, ~300+ = speech.
     */
    private fun calculateRMS(buffer: ByteArray, length: Int): Double {
        var sumSquares = 0.0
        val numSamples = length / 2
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                // Little-endian signed 16-bit sample
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                sumSquares += sample.toDouble() * sample.toDouble()
            }
        }
        return if (numSamples > 0) sqrt(sumSquares / numSamples) else 0.0
    }

    protected fun pcmToWav(pcmData: ByteArray): ByteArray {
        val sampleRate = SAMPLE_RATE
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val headerSize = 44
        val totalDataSize = pcmData.size + headerSize - 8

        val buffer = ByteBuffer.allocate(pcmData.size + headerSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataSize)
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1) // AudioFormat (PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(pcmData.size)
        buffer.put(pcmData)

        return buffer.array()
    }

    protected fun saveTempWavFile(pcmData: ByteArray): File {
        val wavData = pcmToWav(pcmData)
        val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
        FileOutputStream(tempFile).use { it.write(wavData) }
        return tempFile
    }

    override suspend fun stopListening() {
        val activeJob = recordingSessionMutex.withLock {
            // The owner uses non-blocking reads, observes this flag within 10 ms and performs
            // stop()/release() itself. Never stop AudioRecord concurrently with read().
            _isListening.value = false
            recordingJob
        }
        activeJob?.join()
        recordingSessionMutex.withLock {
            if (recordingJob === activeJob) recordingJob = null
        }
    }

    override fun release() {
        runBlocking {
            stopListening()
        }
        serviceScope.cancel()
    }
}
