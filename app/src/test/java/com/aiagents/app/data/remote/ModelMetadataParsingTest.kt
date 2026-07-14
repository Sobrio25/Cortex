package com.aiagents.app.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMetadataParsingTest {

    @Test
    fun `open ai compatible catalog reads kilo context length`() {
        val response = Gson().fromJson(
            """{"data":[{"id":"vendor/coding-model","context_length":262144}]}""",
            ModelsResponse::class.java
        )

        assertEquals("vendor/coding-model", response.data.single().id)
        assertEquals(262_144, response.data.single().contextLength)
    }

    @Test
    fun `open ai compatible catalog reads nvidia max model length`() {
        val response = Gson().fromJson(
            """{"data":[{"id":"nvidia/nemotron","max_model_len":131072}]}""",
            ModelsResponse::class.java
        )

        assertEquals("nvidia/nemotron", response.data.single().id)
        assertEquals(131_072, response.data.single().contextLength)
    }
}
