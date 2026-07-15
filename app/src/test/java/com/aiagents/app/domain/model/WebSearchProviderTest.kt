package com.aiagents.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchProviderTest {
    @Test
    fun `native is the default for missing or invalid stored values`() {
        assertEquals(WebSearchProvider.NATIVE, WebSearchProvider.fromStoredValue(null))
        assertEquals(WebSearchProvider.NATIVE, WebSearchProvider.fromStoredValue("unknown"))
    }

    @Test
    fun `stored provider parsing is case insensitive`() {
        assertEquals(WebSearchProvider.BRAVE, WebSearchProvider.fromStoredValue("brave"))
        assertEquals(WebSearchProvider.SERPAPI, WebSearchProvider.fromStoredValue("SerpApi"))
    }
}
