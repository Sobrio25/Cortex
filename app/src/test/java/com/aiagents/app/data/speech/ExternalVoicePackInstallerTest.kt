package com.aiagents.app.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExternalVoicePackInstallerTest {
    private val manifestUrl =
        "https://cortex-agents-voice-pack.web.app/cortex-voice-pack.json"

    @Test
    fun parsesVersionedSameOriginVoicePack() {
        val manifest = parseExternalVoicePackManifest(
            json = """
                {
                  "versionCode": 5,
                  "splitName": "voice",
                  "url": "https://cortex-agents-voice-pack.web.app/cortex-voice-pack.apk",
                  "sha256": "${"ab".repeat(32)}",
                  "bytes": 35784275
                }
            """.trimIndent(),
            manifestUrl = manifestUrl,
            expectedVersionCode = 5
        )

        assertEquals(5, manifest.versionCode)
        assertEquals("voice", manifest.splitName)
        assertEquals(35_784_275L, manifest.bytes)
    }

    @Test
    fun rejectsPackForAnotherAppVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            parseExternalVoicePackManifest(
                json = validManifest(versionCode = 4),
                manifestUrl = manifestUrl,
                expectedVersionCode = 5
            )
        }
    }

    @Test
    fun rejectsPackHostedOnAnotherOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            parseExternalVoicePackManifest(
                json = validManifest(url = "https://example.com/voice.apk"),
                manifestUrl = manifestUrl,
                expectedVersionCode = 5
            )
        }
    }

    private fun validManifest(
        versionCode: Int = 5,
        url: String = "https://cortex-agents-voice-pack.web.app/cortex-voice-pack.apk"
    ): String = """
        {
          "versionCode": $versionCode,
          "splitName": "voice",
          "url": "$url",
          "sha256": "${"cd".repeat(32)}",
          "bytes": 10
        }
    """.trimIndent()
}
