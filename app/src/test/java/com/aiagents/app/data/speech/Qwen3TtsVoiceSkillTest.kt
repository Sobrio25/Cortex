package com.aiagents.app.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Qwen3TtsVoiceSkillTest {
    @Test
    fun `auto mode detects Qwen custom voice model`() {
        val config = RemoteTtsConfig(
            endpointUrl = "http://192.168.1.20:8880/v1/audio/speech",
            model = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice",
            voice = "Ryan"
        )

        assertEquals(
            RemoteTtsApiFlavor.QWEN3_CUSTOM_VOICE,
            Qwen3TtsVoiceSkill.resolveApiFlavor(config)
        )
        assertTrue(Qwen3TtsVoiceSkill.isActive(config))
    }

    @Test
    fun `voice design sends description without requiring speaker`() {
        val config = RemoteTtsConfig(
            endpointUrl = "http://192.168.1.20:7811/v1/audio/speech/design",
            model = "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign",
            voice = "",
            language = "Spanish",
            voiceDescription = "Voz masculina joven y calida",
            adaptiveStyle = false
        )

        val fields = Qwen3TtsVoiceSkill.requestFields("Hola, soy Cortex.", config)

        assertFalse(Qwen3TtsVoiceSkill.requiresVoice(config))
        assertEquals("Spanish", fields["language"])
        assertEquals("Voz masculina joven y calida", fields["voice_description"])
        assertFalse(fields.containsKey("voice"))
        assertFalse(fields.containsKey("instruct"))
    }

    @Test
    fun `custom voice receives adaptive instruction for each response`() {
        val config = RemoteTtsConfig(
            model = "tts-1",
            voice = "Ryan",
            apiFlavor = RemoteTtsApiFlavor.QWEN3_CUSTOM_VOICE,
            language = "Spanish",
            voiceDescription = "Voz clara y profesional",
            adaptiveStyle = true
        )

        val fields = Qwen3TtsVoiceSkill.requestFields(
            "Advertencia: no se pudo conectar al servidor.",
            config
        )

        assertEquals("Ryan", fields["voice"])
        assertEquals("Spanish", fields["language"])
        assertTrue(fields.getValue("instruct").contains("Voz clara y profesional"))
        assertTrue(fields.getValue("instruct").contains("seriedad calmada"))
    }

    @Test
    fun `plain OpenAI profile keeps the original request schema`() {
        val config = RemoteTtsConfig(
            model = "speaches-ai/Kokoro-82M-v1.0-ONNX",
            voice = "ef_dora",
            apiFlavor = RemoteTtsApiFlavor.OPENAI
        )

        val fields = Qwen3TtsVoiceSkill.requestFields("Hola", config)

        assertEquals(setOf("model", "input", "response_format", "voice"), fields.keys)
        assertEquals("ef_dora", fields["voice"])
    }

    @Test
    fun `PCM streaming profile sends Speaches stream fields`() {
        val config = RemoteTtsConfig(
            model = "speaches-ai/Kokoro-82M-v1.0-ONNX",
            voice = "ef_dora",
            apiFlavor = RemoteTtsApiFlavor.OPENAI,
            audioMode = RemoteTtsAudioMode.STREAMING_PCM
        )

        val fields = Qwen3TtsVoiceSkill.requestFields("Hola", config)

        assertEquals("pcm", fields["response_format"])
        assertEquals("audio", fields["stream_format"])
        assertEquals("ef_dora", fields["voice"])
    }
}
