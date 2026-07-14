package com.aiagents.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleWorkspaceOAuthManagerTest {
    @Test
    fun `selected services request only their required scopes`() {
        val scopes = GoogleWorkspaceOAuthManager.getScopesForServices(listOf("docs", "sheets"))

        assertEquals(
            listOf(
                "https://www.googleapis.com/auth/documents",
                "https://www.googleapis.com/auth/spreadsheets"
            ),
            scopes
        )
    }

    @Test
    fun `recommended scopes contain no redundant readonly or legacy ai grants`() {
        val scopes = GoogleWorkspaceOAuthManager.getRecommendedScopes()

        assertEquals(scopes.distinct(), scopes)
        assertFalse(scopes.any { it.endsWith(".readonly") })
        assertFalse(scopes.any { "generative-language" in it })
        assertTrue("https://www.googleapis.com/auth/documents" in scopes)
        assertTrue("https://www.googleapis.com/auth/drive" in scopes)
    }
}
