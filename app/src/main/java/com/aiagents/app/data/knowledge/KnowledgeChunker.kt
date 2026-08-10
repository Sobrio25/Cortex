package com.aiagents.app.data.knowledge

/**
 * Splits document text into overlapping chunks sized for embedding and retrieval.
 *
 * Strategy: split on sentence/paragraph boundaries, greedily pack sentences into
 * chunks of ~[TARGET_CHARS] characters. Oversized single sentences (no boundary
 * available) are hard-split. Each chunk after the first carries the tail of the
 * previous chunk as overlap so retrieval never loses context across a boundary.
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

        fun flush() {
            val value = current.toString().trim()
            if (value.isNotEmpty()) chunks.add(value)
            current.setLength(0)
        }

        for (sentence in sentences) {
            if (sentence.length > TARGET_CHARS) {
                // A single oversized sentence with no internal boundary: hard-split.
                flush()
                chunks.addAll(hardSplit(sentence))
                continue
            }
            val candidate = if (current.isEmpty()) sentence else "$current $sentence"
            if (candidate.length > TARGET_CHARS) flush()
            if (current.isEmpty()) current.append(sentence) else current.append(' ').append(sentence)
        }
        flush()

        // Overlap: prepend the tail of the previous chunk to every subsequent chunk.
        return chunks.mapIndexed { index, chunk ->
            if (index == 0) {
                chunk
            } else {
                val overlap = chunks[index - 1].takeLast(OVERLAP_CHARS).trim()
                if (overlap.isNotEmpty()) "$overlap $chunk" else chunk
            }
        }
    }

    /** Splits a single oversized sentence into ~[TARGET_CHARS] pieces with [OVERLAP_CHARS] overlap. */
    private fun hardSplit(sentence: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        while (start < sentence.length) {
            val end = minOf(start + TARGET_CHARS, sentence.length)
            pieces.add(sentence.substring(start, end))
            if (end == sentence.length) break
            start = end - OVERLAP_CHARS
        }
        return pieces
    }
}
