package com.aiagents.app.data.speech

/** Makes model output pleasant and safe to send to an Android TTS engine. */
object SpokenTextFormatter {
    private val fencedCode = Regex("```[\\s\\S]*?```")
    private val markdownLink = Regex("\\[([^]]+)]\\([^)]+\\)")
    private val inlineCode = Regex("`([^`]+)`")
    private val markdownSyntax = Regex("(^|\\s)[#>*_~-]+(?=\\s|$)")
    private val repeatedWhitespace = Regex("[ \\t]+")
    private val repeatedBlankLines = Regex("\\n{3,}")

    fun clean(text: String): String = text
        .replace(fencedCode, " Código omitido. ")
        .replace(markdownLink, "$1")
        .replace(inlineCode, "$1")
        .replace(markdownSyntax, "$1")
        .replace(repeatedWhitespace, " ")
        .replace(repeatedBlankLines, "\\n\\n")
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
