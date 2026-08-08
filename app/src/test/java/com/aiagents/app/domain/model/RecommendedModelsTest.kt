package com.aiagents.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedModelsTest {

    @Test
    fun `Gemma 4 LiteRT models are public downloads`() {
        val gemma4Models = RecommendedModels.MODELS.filter { it.id.startsWith("gemma-4-") }

        assertTrue("Expected at least one recommended Gemma 4 model", gemma4Models.isNotEmpty())
        gemma4Models.forEach { model ->
            assertFalse("${model.id} must not require a Hugging Face token", model.requiresHFToken)
            assertFalse("${model.id} must not require gated license acceptance", model.requiresLicense)
        }
    }
}
