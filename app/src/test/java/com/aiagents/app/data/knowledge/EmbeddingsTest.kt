package com.aiagents.app.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingsTest {

    @Test
    fun `byte roundtrip preserves float values`() {
        val original = floatArrayOf(0.0f, 1.0f, -1.5f, 3.25f, 0.0001f)
        val restored = Embeddings.toFloatArray(Embeddings.toBytes(original))
        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals(original[i], restored[i], 1e-6f)
        }
    }

    @Test
    fun `identical vectors have cosine 1`() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        assertEquals(1f, Embeddings.cosine(v, v), 1e-5f)
    }

    @Test
    fun `orthogonal vectors have cosine 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, Embeddings.cosine(a, b), 1e-6f)
    }

    @Test
    fun `opposite vectors have cosine -1`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(-1f, -2f)
        assertEquals(-1f, Embeddings.cosine(a, b), 1e-5f)
    }

    @Test
    fun `zero vectors yield zero similarity instead of NaN`() {
        val zero = floatArrayOf(0f, 0f)
        val v = floatArrayOf(1f, 2f)
        assertEquals(0f, Embeddings.cosine(zero, v), 1e-6f)
        assertEquals(0f, Embeddings.cosine(zero, zero), 1e-6f)
    }

    @Test
    fun `dimension mismatch throws`() {
        try {
            Embeddings.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f))
            assertTrue("expected IllegalArgumentException", false)
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
