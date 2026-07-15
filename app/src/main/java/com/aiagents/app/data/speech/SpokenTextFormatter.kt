package com.aiagents.app.data.speech

/** Makes model output pleasant and safe to send to an Android TTS engine. */
object SpokenTextFormatter {
    private val fencedCode = Regex("```[\\s\\S]*?```")
    private val markdownImage = Regex("!\\[([^]]*)]\\([^)]+\\)")
    private val markdownLink = Regex("\\[([^]]+)]\\([^)]+\\)")
    private val inlineCode = Regex("`([^`]+)`")
    private val boldItalicAsterisk = Regex("\\*{3}([^*\\n]+?)\\*{3}")
    private val boldItalicUnderscore = Regex("_{3}([^_\\n]+?)_{3}")
    private val boldAsterisk = Regex("\\*{2}([^*\\n]+?)\\*{2}")
    private val boldUnderscore = Regex("_{2}([^_\\n]+?)_{2}")
    private val strikethrough = Regex("~{2}([^~\\n]+?)~{2}")
    private val italicAsterisk = Regex("(?<!\\*)\\*([^*\\n]+?)\\*(?!\\*)")
    private val italicUnderscore = Regex("(?<![\\w_])_([^_\\n]+?)_(?![\\w_])")
    private val headingOrQuote = Regex("(?m)^\\s{0,3}(?:#{1,6}|>)\\s*")
    private val unorderedList = Regex("(?m)^\\s*[-+*]\\s+")
    private val orderedList = Regex("(?m)^\\s*(\\d+)[.)]\\s+")
    private val taskCheckbox = Regex("(?i)\\[[ x]]\\s*")
    private val horizontalRule = Regex("(?m)^\\s*[-*_]{3,}\\s*$")
    private val htmlTag = Regex("<[^>]+>")
    private val rawUrl = Regex("https?://[^\\s)>]+")
    private val remainingMarkdownEmphasis = Regex("[*_~]")
    private val repeatedWhitespace = Regex("[ \\t]+")
    private val repeatedBlankLines = Regex("\\n{3,}")

    fun clean(text: String): String = text
        .replace(fencedCode, " Código omitido. ")
        .replace(markdownImage, "$1")
        .replace(markdownLink, "$1")
        .replace(inlineCode, "$1")
        .replace(boldItalicAsterisk, "$1")
        .replace(boldItalicUnderscore, "$1")
        .replace(boldAsterisk, "$1")
        .replace(boldUnderscore, "$1")
        .replace(strikethrough, "$1")
        .replace(italicAsterisk, "$1")
        .replace(italicUnderscore, "$1")
        .replace(horizontalRule, "")
        .replace(headingOrQuote, "")
        .replace(unorderedList, "")
        .replace(orderedList, "$1. ")
        .replace(taskCheckbox, "")
        .replace(htmlTag, "")
        .replace(rawUrl, "enlace")
        .replace(remainingMarkdownEmphasis, "")
        .replace(repeatedWhitespace, " ")
        .replace(repeatedBlankLines, "\n\n")
        .trim()

    fun chunk(text: String, maxLength: Int): List<String> {
        require(maxLength > 0)
        val cleanText = clean(text)
        if (cleanText.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        var remaining = cleanText
        while (remaining.length > maxLength) {
            val window = remaining.take(maxLength)
            val boundary = maxOf(
                window.lastIndexOf(". "),
                window.lastIndexOf("? "),
                window.lastIndexOf("! "),
                window.lastIndexOf("; "),
                window.lastIndexOf(", "),
                window.lastIndexOf(' ')
            ).takeIf { it >= maxLength / 2 } ?: maxLength
            chunks += remaining.take(boundary).trim()
            remaining = remaining.drop(boundary).trimStart()
        }
        if (remaining.isNotBlank()) chunks += remaining
        return chunks
    }
}
