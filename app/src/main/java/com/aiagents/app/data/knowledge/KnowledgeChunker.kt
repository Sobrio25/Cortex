package com.aiagents.app.data.knowledge

/**
 * Splits document text into overlapping chunks sized for embedding and retrieval.
 *
 * Strategy: split on sentence/paragraph boundaries, greedily pack sentences into
 * chunks of ~[TARGET_CHARS] characters, carrying the tail of the previous chunk as
 * overlap so retrieval never loses context across a boundary.
 */
object KnowledgeChunker {

    const val TARGET_CHARS = 400
    const val OVERLAP_CHARS = 60

    private val SENTENCE_SPLIT = Regex("(?<=[.!?;:\\n])\\s+")

    fun chunk(text: String): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()

        val sentences = cleaned
            .split(SENTENCE_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var carriedOverlap = ""

        for (sentence in sentences) {
            val candidate = if (current.isEmpty()) {
                sentence
            } else {
                "$current $sentence"
            }
            if (candidate.length > TARGET_CHARS && current.isNotEmpty()) {
                chunks.add(current.toString())
                carriedOverlap = overlapOf(current.toString())
                current.setLength(0)
                current.append(carriedOverlap)
                if (carriedOverlap.isNotEmpty()) current.append(' ')
                current.append(sentence)
            } else {
                if (current.isEmpty() && carriedOverlap.isNotEmpty()) {
                    current.append(carriedOverlap).append(' ')
                }
                current.append(if (current.isEmpty()) sentence else " $sentence")
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks
    }

    /** Trailing ~[OVERLAP_CHARS] chars of a chunk, bounded to the last sentence(s). */
    private fun overlapOf(chunk: String): String {
        val tail = chunk.takeLast(OVERLAP_CHARS).trim()
        val lastBoundary = tail.lastIndexOf(' ')
        return (if (lastBoundary > 0) tail.substring(lastBoundary + 1) else tail).trim()
    }
}
