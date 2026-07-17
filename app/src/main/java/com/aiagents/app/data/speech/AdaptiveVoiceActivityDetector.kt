package com.aiagents.app.data.speech

import kotlin.math.max
import kotlin.math.min

internal enum class VoiceStopReason {
    SILENCE_AFTER_SPEECH,
    NO_SPEECH,
    MAX_DURATION
}

internal data class VoiceActivityUpdate(
    val hasSpoken: Boolean,
    val shouldStop: Boolean,
    val stopReason: VoiceStopReason? = null,
    val noiseFloorRms: Double,
    val speechThresholdRms: Double,
    val silenceThresholdRms: Double,
    val elapsedSeconds: Double
)

/**
 * Small adaptive endpoint detector for microphone PCM frames.
 *
 * Android microphones and their automatic gain control produce very different RMS floors. A
 * fixed amplitude threshold can therefore interpret steady room noise as continuous speech. This
 * detector learns the quiet floor briefly, uses hysteresis after speech starts, and tolerates
 * isolated noise spikes while accumulating end-of-speech silence.
 */
internal class AdaptiveVoiceActivityDetector(
    private val sampleRate: Int,
    silenceTimeoutMs: Int = 1_500,
    noSpeechTimeoutMs: Int = 6_000,
    maxRecordingMs: Int = 30_000
) {
    private val calibrationSamples = sampleRate * 400 / 1_000
    private val silenceLimit = sampleRate * silenceTimeoutMs / 1_000
    private val noSpeechLimit = sampleRate * noSpeechTimeoutMs / 1_000
    private val maxSamples = sampleRate * maxRecordingMs / 1_000

    private var totalSamples = 0
    private var quietSamples = 0
    private var hasSpoken = false
    private var noiseFloorRms = 350.0
    private var speechPeakRms = 0.0

    fun accept(rms: Double, samplesRead: Int): VoiceActivityUpdate {
        if (samplesRead <= 0) return currentUpdate(null)
        totalSamples += samplesRead

        if (!hasSpoken) {
            // Values under this ceiling are plausible room noise and can safely refine the floor.
            // Louder early frames are retained as possible immediate speech instead.
            if (rms < CALIBRATION_SPEECH_CEILING_RMS) {
                val durationSeconds = samplesRead.toDouble() / sampleRate
                val upwardAlpha = (durationSeconds / 0.45).coerceIn(0.02, 0.65)
                val alpha = if (rms < noiseFloorRms) 0.45 else upwardAlpha
                noiseFloorRms += (rms - noiseFloorRms) * alpha
                noiseFloorRms = noiseFloorRms.coerceIn(80.0, 2_500.0)
            }

            val speechThreshold = speechThreshold()
            val calibrationComplete = totalSamples >= calibrationSamples
            val immediateSpeech = rms >= IMMEDIATE_SPEECH_RMS
            if (immediateSpeech || calibrationComplete && rms >= speechThreshold) {
                hasSpoken = true
                speechPeakRms = rms
                quietSamples = 0
            }
        } else {
            speechPeakRms = max(speechPeakRms, rms)
            val silenceThreshold = silenceThreshold()
            val clearSpeechThreshold = max(noiseFloorRms * 1.55, silenceThreshold * 1.18)
            if (rms >= clearSpeechThreshold) {
                // A single knock or handling noise should not erase the whole quiet interval.
                quietSamples = (quietSamples - samplesRead * 2).coerceAtLeast(0)
            } else {
                quietSamples += samplesRead
            }
        }

        val reason = when {
            hasSpoken && quietSamples >= silenceLimit -> VoiceStopReason.SILENCE_AFTER_SPEECH
            !hasSpoken && totalSamples >= noSpeechLimit -> VoiceStopReason.NO_SPEECH
            totalSamples >= maxSamples -> VoiceStopReason.MAX_DURATION
            else -> null
        }
        return currentUpdate(reason)
    }

    private fun currentUpdate(reason: VoiceStopReason?): VoiceActivityUpdate =
        VoiceActivityUpdate(
            hasSpoken = hasSpoken,
            shouldStop = reason != null,
            stopReason = reason,
            noiseFloorRms = noiseFloorRms,
            speechThresholdRms = speechThreshold(),
            silenceThresholdRms = if (hasSpoken) silenceThreshold() else 0.0,
            elapsedSeconds = totalSamples.toDouble() / sampleRate
        )

    private fun speechThreshold(): Double = max(MIN_SPEECH_RMS, noiseFloorRms * 1.65)

    private fun silenceThreshold(): Double = max(
        MIN_SILENCE_RMS,
        max(noiseFloorRms * 1.30, min(speechPeakRms * 0.35, MAX_SILENCE_RMS))
    )

    companion object {
        private const val CALIBRATION_SPEECH_CEILING_RMS = 1_400.0
        private const val IMMEDIATE_SPEECH_RMS = 2_200.0
        private const val MIN_SPEECH_RMS = 700.0
        private const val MIN_SILENCE_RMS = 550.0
        private const val MAX_SILENCE_RMS = 1_800.0
    }
}
