package com.aiagents.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceRepositoryTest {

    @Test
    fun `next link parser accepts only Hugging Face model API pages`() {
        val valid = "<https://huggingface.co/api/models?search=gemma&cursor=abc%3D%3D>; rel=\"next\""
        val malicious = "<https://huggingface.co.evil.example/api/models?cursor=abc>; rel=\"next\""
        val unrelated = "<https://huggingface.co/api/datasets?cursor=abc>; rel=\"next\""

        assertEquals(
            "https://huggingface.co/api/models?search=gemma&cursor=abc%3D%3D",
            HuggingFaceRepository.parseNextPageUrl(valid)
        )
        assertNull(HuggingFaceRepository.parseNextPageUrl(malicious))
        assertNull(HuggingFaceRepository.parseNextPageUrl(unrelated))
        assertNull(HuggingFaceRepository.parseNextPageUrl(null))
    }

    @Test
    fun `repository parser accepts ids and canonical Hugging Face urls`() {
        assertEquals(
            "google/gemma-3n-E2B-it-litert-preview",
            HuggingFaceRepository.parseRepoIdFromUrl(
                "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/tree/main"
            )
        )
        assertEquals(
            "org/model",
            HuggingFaceRepository.parseRepoIdFromUrl("org/model")
        )
        assertNull(
            HuggingFaceRepository.parseRepoIdFromUrl(
                "https://huggingface.co.evil.example/google/gemma"
            )
        )
    }

    @Test
    fun `litertlm is recognized as a local LLM bundle`() {
        assertEquals(
            HFLocalModelFormat.LITERT_LM,
            HuggingFaceRepository.detectLocalFormat(
                repoId = "example/qwen-litertlm",
                fileName = "model.litertlm",
                tags = emptyList(),
                libraryName = "litert-lm"
            )
        )
    }

    @Test
    fun `generic pytorch bin is never offered as a MediaPipe model`() {
        assertNull(
            HuggingFaceRepository.detectLocalFormat(
                repoId = "google/gemma-transformers",
                fileName = "pytorch_model.bin",
                tags = listOf("text-generation", "transformers"),
                libraryName = "transformers"
            )
        )
    }

    @Test
    fun `task bundle requires LLM metadata and excludes web variants`() {
        val llmTask = HuggingFaceRepository.detectLocalFormat(
            repoId = "litert-community/gemma",
            fileName = "gemma.task",
            tags = listOf("text-generation"),
            libraryName = null
        )
        val visionTask = HuggingFaceRepository.detectLocalFormat(
            repoId = "google/face-landmarker",
            fileName = "face_landmarker.task",
            tags = listOf("computer-vision"),
            libraryName = "mediapipe"
        )
        val webTask = HuggingFaceRepository.detectLocalFormat(
            repoId = "litert-community/gemma",
            fileName = "gemma-web.task",
            tags = listOf("text-generation"),
            libraryName = null
        )

        assertEquals(HFLocalModelFormat.MEDIAPIPE_TASK, llmTask)
        assertNull(visionTask)
        assertNull(webTask)
    }

    @Test
    fun `MediaPipe bin requires both LLM and Android runtime signals`() {
        val compatible = HuggingFaceRepository.detectLocalFormat(
            repoId = "google/gemma-2b-it-tflite",
            fileName = "gemma-2b-it-cpu-int4.bin",
            tags = listOf("text-generation", "tflite"),
            libraryName = "tflite"
        )
        val transformerWeights = HuggingFaceRepository.detectLocalFormat(
            repoId = "example/gemma",
            fileName = "model.bin",
            tags = listOf("text-generation", "transformers"),
            libraryName = "transformers"
        )

        assertEquals(HFLocalModelFormat.MEDIAPIPE_BIN, compatible)
        assertNull(transformerWeights)
        assertTrue(compatible != transformerWeights)
    }
}
