package com.aiagents.app.data.speech

/** Makes model output pleasant and safe to send to an Android TTS engine. */
object SpokenTextFormatter {
    private val fencedCode = Regex("```[\\s\\S]*?```")
    private val structuredPayload = Regex(
        "(?s)\\s*(?:WEATHER_DATA|CALENDAR_DATA|REMINDER_DATA|TASK_DATA)\\s*\\{.*$"
    )
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
    private val rawUrl = Regex(
        "(?i)\\b(?:https?://|www\\.)[^\\s<>\\(\\)\\[\\]\\{\\}]+"
    )
    private val remainingMarkdownEmphasis = Regex("[*_~]")
    private val markdownTablePipe = Regex("\\s*\\|\\s*")
    private val markdownTableDivider = Regex(
        "(?m)^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$"
    )
    private val repeatedWhitespace = Regex("[ \\t]+")
    private val repeatedBlankLines = Regex("\\n{3,}")

    fun clean(text: String): String = removeEmoji(text)
        .replace(structuredPayload, "")
        .replace(fencedCode, " ")
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
        .replace(rawUrl, "")
        .replace(markdownTableDivider, "")
        .replace(markdownTablePipe, " ")
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
            val punctuationBoundary = listOf(". ", "? ", "! ", "; ", ", ")
                .maxOf { delimiter -> window.lastIndexOf(delimiter) }
                .takeIf { it >= maxLength / 2 }
                ?.plus(1)
            val wordBoundary = window.lastIndexOf(' ').takeIf { it >= maxLength / 2 }
            val boundary = punctuationBoundary ?: wordBoundary ?: maxLength
            chunks += remaining.take(boundary).trim()
            remaining = remaining.drop(boundary).trimStart()
        }
        if (remaining.isNotBlank()) chunks += remaining
        return chunks
    }

    private fun removeEmoji(text: String): String {
        val output = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (!isEmojiCodePoint(codePoint)) output.appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
        return output.toString()
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint == 0x200D ||
            codePoint == 0x20E3 ||
            codePoint == 0xFE0E ||
            codePoint == 0xFE0F
}
