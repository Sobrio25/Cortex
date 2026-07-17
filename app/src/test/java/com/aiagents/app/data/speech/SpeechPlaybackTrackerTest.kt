package com.aiagents.app.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlaybackTrackerTest {
    @Test
    fun `chunks receive one ordered utterance id each`() {
        val tracker = SpeechPlaybackTracker()

        val request = tracker.start(listOf("uno", "dos", "tres"))

        assertEquals(request.chunks.size, request.utteranceIds.size)
        assertEquals(request.utteranceIds.size, request.utteranceIds.distinct().size)
        assertTrue(request.utteranceIds.all(tracker::owns))
        assertTrue(tracker.isFinal(request, request.utteranceIds.last()))
        assertFalse(tracker.isFinal(request, request.utteranceIds.first()))
    }

    @Test
    fun `interrupted request callbacks cannot affect replacement request`() {
        val tracker = SpeechPlaybackTracker()
        val interrupted = tracker.start(listOf("respuesta anterior"))
        tracker.cancel()
        val replacement = tracker.start(listOf("respuesta", "nueva"))

        assertFalse(tracker.owns(interrupted.finalUtteranceId))
        assertTrue(tracker.owns(replacement.utteranceIds.first()))
        assertTrue(tracker.isFinal(replacement, replacement.finalUtteranceId))
    }
}
