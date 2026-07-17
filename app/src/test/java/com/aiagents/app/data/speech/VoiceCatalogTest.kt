package com.aiagents.app.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {
    @Test
    fun `downloadable assets have unique ids and realistic sizes`() {
        val assets = VoiceCatalog.downloadableAssets

        assertEquals(assets.size, assets.map { it.id }.distinct().size)
        assertTrue(VoiceCatalog.whisperTiny.downloadBytes > 100L * 1024L * 1024L)
        assertTrue(VoiceCatalog.piperAld.downloadBytes in 20L * 1024L * 1024L..22L * 1024L * 1024L)
        assertTrue(VoiceCatalog.piperClaude.downloadBytes in 20L * 1024L * 1024L..22L * 1024L * 1024L)
        assertNull(VoiceCatalog.find("missing"))
    }

    @Test
    fun `voice selections use mandatory Android STT for missing or stale values`() {
        assertEquals(AssistantSttMode.ANDROID, AssistantSttMode.fromId(null))
        assertEquals(AssistantSttMode.ANDROID, AssistantSttMode.fromId("vosk"))
        assertEquals(AssistantTtsMode.NONE, AssistantTtsMode.fromId(null))
        assertEquals(AssistantTtsMode.NONE, AssistantTtsMode.fromId("legacy_tts"))
        assertEquals(AssistantSttMode.REMOTE_SERVER, AssistantSttMode.fromId("remote_whisper"))
        assertEquals(AssistantTtsMode.REMOTE_SERVER, AssistantTtsMode.fromId("remote_tts"))
    }

    @Test
    fun `remote voice endpoints require full http urls and model details`() {
        assertTrue(
            SelfHostedVoiceApi.isConfigured(
                RemoteSttConfig("http://192.168.1.20:8000/v1/audio/transcriptions", "whisper-1")
            )
        )
        assertTrue(
            SelfHostedVoiceApi.isConfigured(
                RemoteTtsConfig("https://voice.example.test/v1/audio/speech", "tts-1", "alloy")
            )
        )
        assertEquals(
            "La URL del endpoint no es valida",
            SelfHostedVoiceApi.endpointValidationError("192.168.1.20:8000")
        )
    }

    @Test
    fun `streamed wav sizes are repaired before Android playback`() {
        val wav = ByteArray(52) { 0 }
        "RIFF".encodeToByteArray().copyInto(wav, 0)
        byteArrayOf(-1, -1, -1, -1).copyInto(wav, 4)
        "WAVEfmt ".encodeToByteArray().copyInto(wav, 8)
        "data".encodeToByteArray().copyInto(wav, 36)
        byteArrayOf(-1, -1, -1, -1).copyInto(wav, 40)

        val normalized = SelfHostedVoiceApi.normalizeTtsAudio(wav, "audio/wav")

        assertEquals(".wav", normalized.fileExtension)
        assertEquals(44, normalized.bytes.littleEndianInt(4))
        assertEquals(8, normalized.bytes.littleEndianInt(40))
    }

    @Test
    fun `piper modes point to downloadable assets`() {
        listOf(AssistantTtsMode.PIPER_ALD, AssistantTtsMode.PIPER_CLAUDE).forEach { mode ->
            val assetId = requireNotNull(mode.assetId)
            assertEquals(VoiceAssetKind.TTS_VOICE, VoiceCatalog.find(assetId)?.kind)
        }
    }

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
}
