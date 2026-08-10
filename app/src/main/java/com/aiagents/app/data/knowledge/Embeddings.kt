package com.aiagents.app.data.knowledge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Pure helpers for on-device semantic embeddings (unit-testable, no Android deps). */
object Embeddings {
    private const val FLOAT_BYTES = 4

    fun toBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * FLOAT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / FLOAT_BYTES)
        for (i in out.indices) out[i] = buffer.float
        return out
    }

    /** Cosine similarity in [-1, 1]; returns 0 for zero vectors. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding dimensions differ: ${a.size} vs ${b.size}" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dot / denominator
    }
}
