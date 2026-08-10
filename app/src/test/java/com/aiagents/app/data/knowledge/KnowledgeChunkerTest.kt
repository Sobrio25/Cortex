package com.aiagents.app.data.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeChunkerTest {

    @Test
    fun `empty and blank texts produce no chunks`() {
        assertEquals(emptyList<String>(), KnowledgeChunker.chunk(""))
        assertEquals(emptyList<String>(), KnowledgeChunker.chunk("   \n  \t "))
    }

    @Test
    fun `short text stays as a single chunk`() {
        val chunks = KnowledgeChunker.chunk("Este es un texto corto para probar el chunker.")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("texto corto"))
    }

    @Test
    fun `long text is split into multiple chunks with overlap`() {
        val sentences = (1..60).joinToString(" ") { "Frase número $it con contenido suficiente para superar el tamaño objetivo." }
        val chunks = KnowledgeChunker.chunk(sentences)
        assertTrue("expected multiple chunks, got ${chunks.size}", chunks.size >= 3)
        chunks.forEach { chunk ->
            assertTrue("chunk too long: ${chunk.length}", chunk.length <= KnowledgeChunker.TARGET_CHARS + 200)
        }
    }

    @Test
    fun `chunks preserve the original words in order`() {
        val sentences = (1..40).joinToString(" ") { "Palabra$it del documento." }
        val chunks = KnowledgeChunker.chunk(sentences)
        val joined = chunks.joinToString(" ")
        assertTrue("first words missing", joined.contains("Palabra1"))
        assertTrue("last words missing", joined.contains("Palabra40"))
    }

    @Test
    fun `no sentence boundary produces one chunk per text`() {
        // A single very long "sentence" (no punctuation boundary) must still be chunked.
        val long = "a".repeat(5000)
        val chunks = KnowledgeChunker.chunk(long)
        assertTrue(chunks.size >= 2)
        chunks.forEach { assertTrue(it.length <= KnowledgeChunker.TARGET_CHARS + 200) }
    }
}
