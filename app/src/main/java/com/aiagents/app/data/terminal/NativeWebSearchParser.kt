package com.aiagents.app.data.terminal

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder

internal data class NativeWebSearchItem(
    val title: String,
    val url: String,
    val snippet: String
)

/** Parses both DuckDuckGo HTML and Lite result pages without executing JavaScript. */
internal object NativeWebSearchParser {
    fun parse(html: String, maxResults: Int): List<NativeWebSearchItem> {
        if (html.isBlank() || maxResults <= 0) return emptyList()
        val document = Jsoup.parse(html, "https://duckduckgo.com/")
        val results = mutableListOf<NativeWebSearchItem>()

        document.select("div.result, article[data-testid=result]").forEach { container ->
            if (container.classNames().any { it.contains("ad", ignoreCase = true) } ||
                container.attr("data-testid").contains("ad", ignoreCase = true)
            ) return@forEach
            val link = container.selectFirst("a.result__a, a[data-testid=result-title-a]")
                ?: return@forEach
            addResult(
                output = results,
                link = link,
                snippet = container.selectFirst(".result__snippet, [data-result=snippet]")?.text().orEmpty()
            )
        }

        if (results.isEmpty()) {
            val snippets = document.select(".result-snippet")
            document.select("a.result-link").forEachIndexed { index, link ->
                addResult(results, link, snippets.getOrNull(index)?.text().orEmpty())
            }
        }

        return results
            .distinctBy { it.url.lowercase() }
            .take(maxResults)
    }

    private fun addResult(
        output: MutableList<NativeWebSearchItem>,
        link: Element,
        snippet: String
    ) {
        val title = link.text().trim()
        val rawUrl = link.attr("href").ifBlank { link.absUrl("href") }
        val url = decodeResultUrl(rawUrl)
        if (title.isBlank() || !url.startsWith("http://") && !url.startsWith("https://")) return
        output += NativeWebSearchItem(title, url, snippet.trim())
    }

    private fun decodeResultUrl(rawUrl: String): String {
        val absolute = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> "https://duckduckgo.com$rawUrl"
            else -> rawUrl
        }
        val encodedDestination = Regex("[?&]uddg=([^&]+)")
            .find(absolute)
            ?.groupValues
            ?.getOrNull(1)
            ?: return absolute
        return runCatching { URLDecoder.decode(encodedDestination, Charsets.UTF_8.name()) }
            .getOrDefault(absolute)
    }
}
