package com.aiagents.app.data.speech

import java.util.UUID

internal data class SpeechPlaybackRequest(
    val requestId: String,
    val chunks: List<String>,
    val utteranceIds: List<String>
) {
    val finalUtteranceId: String = utteranceIds.last()
}

/** Keeps callbacks from an interrupted TTS request from changing the next request's state. */
internal class SpeechPlaybackTracker {
    @Volatile
    private var activeRequestId: String? = null

    fun start(chunks: List<String>): SpeechPlaybackRequest {
        require(chunks.isNotEmpty())
        val requestId = UUID.randomUUID().toString()
        activeRequestId = requestId
        return SpeechPlaybackRequest(
            requestId = requestId,
            chunks = chunks.toList(),
            utteranceIds = chunks.indices.map { index -> "cortex-$requestId-$index" }
        )
    }

    fun owns(utteranceId: String?): Boolean =
        utteranceId != null && activeRequestId?.let { utteranceId.startsWith("cortex-$it-") } == true

    fun isFinal(request: SpeechPlaybackRequest, utteranceId: String?): Boolean =
        owns(utteranceId) && request.finalUtteranceId == utteranceId

    fun cancel() {
        activeRequestId = null
    }
}
