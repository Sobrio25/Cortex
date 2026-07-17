package com.aiagents.app.data.terminal

/**
 * Bounds persisted tool output before it is sent back to a model.
 *
 * The complete result can remain in local storage/UI, while provider history receives a compact
 * head/tail receipt. Keeping both ends is especially useful for command output where the error or
 * summary is commonly printed last.
 */
object ToolOutputBudget {
    const val PROVIDER_CHAR_LIMIT = 12_000

    fun compactForProvider(
        content: String,
        maxChars: Int = PROVIDER_CHAR_LIMIT
    ): String {
        require(maxChars >= MIN_CHAR_LIMIT) { "maxChars must be at least $MIN_CHAR_LIMIT" }
        if (content.length <= maxChars || content.startsWith("data:image/")) return content

        val marker = "\n\n... [tool output truncated; full result is stored locally] ...\n\n"
        val available = maxChars - marker.length
        val headChars = available * 2 / 3
        val tailChars = available - headChars

        return buildString(maxChars) {
            append(content.take(headChars))
            append(marker)
            append(content.takeLast(tailChars))
        }
    }

    private const val MIN_CHAR_LIMIT = 256
}
