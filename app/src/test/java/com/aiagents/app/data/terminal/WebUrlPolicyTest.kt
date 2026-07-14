package com.aiagents.app.data.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class WebUrlPolicyTest {
    @Test
    fun acceptsNormalPublicHttpsUrl() {
        assertTrue(WebUrlPolicy.validateUrl("https://example.com/news?id=1").isSuccess)
    }

    @Test
    fun rejectsLocalHostsAndCredentials() {
        assertTrue(WebUrlPolicy.validateUrl("http://localhost:8080/admin").isFailure)
        assertTrue(WebUrlPolicy.validateUrl("http://service.internal/data").isFailure)
        assertTrue(WebUrlPolicy.validateUrl("https://user:pass@example.com").isFailure)
    }

    @Test
    fun rejectsPrivateAndLoopbackAddresses() {
        listOf("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.1.1", "::1", "fc00::1")
            .forEach { address ->
                assertFalse("Expected $address to be private", WebUrlPolicy.isPublicAddress(InetAddress.getByName(address)))
            }
    }

    @Test
    fun acceptsPublicAddress() {
        assertTrue(WebUrlPolicy.isPublicAddress(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun rejectsReservedDocumentationAndMappedAddresses() {
        listOf(
            "192.0.2.1",
            "198.51.100.10",
            "203.0.113.5",
            "2001:db8::1",
            "2002:7f00:1::",
            "::ffff:127.0.0.1"
        ).forEach { address ->
            assertFalse(
                "Expected $address to be non-public",
                WebUrlPolicy.isPublicAddress(InetAddress.getByName(address))
            )
        }
    }

    @Test
    fun rejectsAbbreviatedNumericLoopbackHosts() {
        assertFalse(WebUrlPolicy.isAllowedHostname("127.1"))
        assertFalse(WebUrlPolicy.isAllowedHostname("2130706433"))
    }
}
