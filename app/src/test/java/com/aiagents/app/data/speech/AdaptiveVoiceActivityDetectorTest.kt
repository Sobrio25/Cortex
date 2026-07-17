package com.aiagents.app.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveVoiceActivityDetectorTest {
    private val sampleRate = 16_000
    private val frameSamples = sampleRate / 10

    @Test
    fun `steady room noise does not prevent endpoint after speech`() {
        val detector = AdaptiveVoiceActivityDetector(sampleRate)

        repeat(6) { detector.accept(rms = 900.0, samplesRead = frameSamples) }
        repeat(12) { detector.accept(rms = 3_200.0, samplesRead = frameSamples) }
        var result = detector.accept(rms = 900.0, samplesRead = frameSamples)
        repeat(16) { result = detector.accept(rms = 900.0, samplesRead = frameSamples) }

        assertTrue(result.hasSpoken)
        assertTrue(result.shouldStop)
        assertEquals(VoiceStopReason.SILENCE_AFTER_SPEECH, result.stopReason)
    }

    @Test
    fun `brief noise spike does not erase accumulated silence`() {
        val detector = AdaptiveVoiceActivityDetector(sampleRate)

        repeat(5) { detector.accept(rms = 350.0, samplesRead = frameSamples) }
        repeat(8) { detector.accept(rms = 2_800.0, samplesRead = frameSamples) }
        repeat(10) { detector.accept(rms = 350.0, samplesRead = frameSamples) }
        detector.accept(rms = 2_200.0, samplesRead = frameSamples)
        var result = detector.accept(rms = 350.0, samplesRead = frameSamples)
        repeat(8) { result = detector.accept(rms = 350.0, samplesRead = frameSamples) }

        assertTrue(result.shouldStop)
        assertEquals(VoiceStopReason.SILENCE_AFTER_SPEECH, result.stopReason)
    }

    @Test
    fun `room noise without speech ends with no speech timeout`() {
        val detector = AdaptiveVoiceActivityDetector(sampleRate)
        var result = detector.accept(rms = 650.0, samplesRead = frameSamples)
        repeat(60) { result = detector.accept(rms = 650.0, samplesRead = frameSamples) }

        assertFalse(result.hasSpoken)
        assertTrue(result.shouldStop)
        assertEquals(VoiceStopReason.NO_SPEECH, result.stopReason)
    }
}
