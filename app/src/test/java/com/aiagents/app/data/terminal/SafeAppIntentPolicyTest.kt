package com.aiagents.app.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeAppIntentPolicyTest {
    @Test
    fun `web URLs only accept http and https without credentials`() {
        assertTrue(SafeAppIntentPolicy.isValidWebUrl("https://example.com/path?q=android"))
        assertTrue(SafeAppIntentPolicy.isValidWebUrl("http://example.com"))
        assertFalse(SafeAppIntentPolicy.isValidWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(SafeAppIntentPolicy.isValidWebUrl("javascript:alert(1)"))
        assertFalse(SafeAppIntentPolicy.isValidWebUrl("https://user:secret@example.com"))
    }

    @Test
    fun `package names cannot inject URI or intent syntax`() {
        assertTrue(SafeAppIntentPolicy.isValidPackageName("com.example.app"))
        assertFalse(SafeAppIntentPolicy.isValidPackageName("package:com.example.app"))
        assertFalse(SafeAppIntentPolicy.isValidPackageName("com.example.app;end"))
    }

    @Test
    fun `phone normalization rejects USSD and keeps a safe dial number`() {
        assertEquals("+525512345678", SafeAppIntentPolicy.normalizePhoneNumber("+52 (55) 1234-5678"))
        assertNull(SafeAppIntentPolicy.normalizePhoneNumber("*123#"))
        assertNull(SafeAppIntentPolicy.normalizePhoneNumber("12"))
    }

    @Test
    fun `capability URI rejects arbitrary schemes and file paths`() {
        assertTrue(SafeAppIntentPolicy.isValidCapabilityUri("view", "https://example.com"))
        assertTrue(SafeAppIntentPolicy.isValidCapabilityUri("open_file", "content://example/document/1"))
        assertFalse(SafeAppIntentPolicy.isValidCapabilityUri("view", "spotify:track:123"))
        assertFalse(SafeAppIntentPolicy.isValidCapabilityUri("open_file", "file:///sdcard/private.txt"))
    }

    @Test
    fun `oversized shared text is rejected`() {
        assertTrue(SafeAppIntentPolicy.isValidShareText("Texto que el usuario revisará"))
        assertFalse(
            SafeAppIntentPolicy.isValidShareText(
                "x".repeat(SafeAppIntentPolicy.MAX_SHARE_TEXT_LENGTH + 1)
            )
        )
    }
}
