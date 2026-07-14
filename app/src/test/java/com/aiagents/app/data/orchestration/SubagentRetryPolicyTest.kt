package com.aiagents.app.data.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRetryPolicyTest {
    @Test
    fun rateLimitsReceiveLongerBoundedBackoffAndOneExtraRetry() {
        val error = IllegalStateException("HTTP 429: rate limit exceeded")

        assertTrue(SubagentRetryPolicy.isRateLimited(error))
        assertEquals(3, SubagentRetryPolicy.maxRetries(error))
        assertEquals(2_000L, SubagentRetryPolicy.delayMillis(error, 1))
        assertEquals(8_000L, SubagentRetryPolicy.delayMillis(error, 3))
    }

    @Test
    fun ordinaryTransportFailuresKeepFastRetryPolicy() {
        val error = IllegalStateException("connection reset")

        assertFalse(SubagentRetryPolicy.isRateLimited(error))
        assertEquals(2, SubagentRetryPolicy.maxRetries(error))
        assertEquals(500L, SubagentRetryPolicy.delayMillis(error, 1))
    }
}
