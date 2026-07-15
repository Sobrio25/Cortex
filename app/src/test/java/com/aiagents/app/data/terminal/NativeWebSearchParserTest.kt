package com.aiagents.app.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWebSearchParserTest {
    @Test
    fun `parses html results and decodes redirect urls`() {
        val html = """
            <html><body>
              <div class="result">
                <h2><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fguide&amp;rut=x">Example <b>Guide</b></a></h2>
                <div class="result__snippet">Useful &amp; current information.</div>
              </div>
            </body></html>
        """.trimIndent()

        val result = NativeWebSearchParser.parse(html, 5).single()

        assertEquals("Example Guide", result.title)
        assertEquals("https://example.com/guide", result.url)
        assertEquals("Useful & current information.", result.snippet)
    }

    @Test
    fun `parses lite results removes duplicates and respects limit`() {
        val html = """
            <html><body><table>
              <tr><td><a class="result-link" href="https://one.example">One</a></td></tr>
              <tr><td class="result-snippet">First result</td></tr>
              <tr><td><a class="result-link" href="https://one.example">One duplicate</a></td></tr>
              <tr><td class="result-snippet">Duplicate</td></tr>
              <tr><td><a class="result-link" href="https://two.example">Two</a></td></tr>
              <tr><td class="result-snippet">Second result</td></tr>
            </table></body></html>
        """.trimIndent()

        val results = NativeWebSearchParser.parse(html, 1)

        assertEquals(1, results.size)
        assertEquals("https://one.example", results.single().url)
        assertTrue(results.single().snippet.contains("First"))
    }
}
