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

    protected var audioRecord: AudioRecord? = null
    protected var recordingJob: Job? = null
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioRecordLock = Any()

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_MULTIPLIER = 2

        // Silence detection
        private const val VOICE_THRESHOLD = 300.0
        private const val SILENCE_TIMEOUT_SECONDS = 2
        private const val NO_SPEECH_TIMEOUT_SECONDS = 5
        private const val MAX_RECORDING_SECONDS = 60
    }

    protected fun startRecording(): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("BaseSTTService", "Permiso de micrófono no concedido")
            return null
        }
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE * BUFFER_SIZE_MULTIPLIER)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("BaseSTTService", "AudioRecord no se pudo inicializar")
                stopCurrentAudioRecord(release = true)
                return null
            }

            audioRecord?.startRecording()
            _isListening.value = true

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)

            // Silence detection state
            var silentSamples = 0
            var totalSamples = 0
            var hasSpoken = false
            val silenceLimit = SAMPLE_RATE * SILENCE_TIMEOUT_SECONDS
            val noSpeechLimit = SAMPLE_RATE * NO_SPEECH_TIMEOUT_SECONDS
            val maxSamples = SAMPLE_RATE * MAX_RECORDING_SECONDS

            while (_isListening.value && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    val samplesRead = read / 2 // 16-bit = 2 bytes per sample
                    totalSamples += samplesRead

                    val rms = calculateRMS(buffer, read)
                    if (rms > VOICE_THRESHOLD) {
                        hasSpoken = true
                        silentSamples = 0
                    } else if (hasSpoken) {
                        silentSamples += samplesRead
                        if (silentSamples >= silenceLimit) {
                            Log.d("BaseSTTService", "Auto-stop: silencio detectado tras habla")
                            break
                        }
                    }

                    // No speech at all timeout
                    if (!hasSpoken && totalSamples >= noSpeechLimit) {
                        Log.d("BaseSTTService", "Auto-stop: sin habla detectada")
                        break
                    }

                    // Max duration
                    if (totalSamples >= maxSamples) {
                        Log.d("BaseSTTService", "Auto-stop: duracion maxima alcanzada")
                        break
                    }
                }
            }

            // Cleanup audio hardware (handles both auto-stop and manual stop)
            _isListening.value = false
            stopCurrentAudioRecord(release = true)

            return outputStream.toByteArray()

        } catch (e: Exception) {
            Log.e("BaseSTTService", "Error grabando audio", e)
            _isListening.value = false
            stopCurrentAudioRecord(release = true)
            return null
        }
    }

    /** Serializes manual and silence-triggered cleanup so AudioRecord.stop() is idempotent. */
    private fun stopCurrentAudioRecord(release: Boolean) {
        synchronized(audioRecordLock) {
            val record = audioRecord ?: return
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (error: IllegalStateException) {
                Log.w("BaseSTTService", "AudioRecord ya estaba detenido")
            }
            if (release) {
                runCatching { record.release() }
                    .onFailure { Log.w("BaseSTTService", "No se pudo liberar AudioRecord", it) }
                audioRecord = null
            }
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
        _isListening.value = false
        // Calling stop() unblocks the read() call in startRecording() for immediate response.
        // startRecording() handles release() and cleanup after the loop exits.
        stopCurrentAudioRecord(release = false)
        recordingJob?.join()
    }

    override fun release() {
        runBlocking {
            stopListening()
        }
        serviceScope.cancel()
    }
}
