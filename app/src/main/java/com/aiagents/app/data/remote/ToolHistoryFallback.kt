package com.aiagents.app.data.remote

/**
 * Converts native OpenAI tool-call history into ordinary conversation turns.
 *
 * Some OpenAI-compatible upstreams accept tool definitions and can emit a tool call, but reject
 * the otherwise valid assistant(tool_calls) -> tool continuation. This representation preserves
 * the call and its result while avoiding provider-specific tool-call identifiers on a retry.
 */
internal fun flattenToolHistoryForCompatibility(messages: List<ChatMessage>): List<ChatMessage> {
    val flattened = messages.mapNotNull { message ->
        when {
            // The result below contains everything needed to continue. Omitting the assistant's
            // native call prevents strict providers from echoing an internal call transcript.
            message.role == "assistant" && !message.toolCalls.isNullOrEmpty() -> null

            message.role == "assistant" && isCompatibilityTranscript(message.content) -> null

            message.role == "tool" -> {
                val toolName = message.name?.takeIf(String::isNotBlank) ?: "tool"
                ChatMessage(
                    role = "user",
                    content = buildString {
                        append("APPLICATION TOOL RESULT from ")
                        append(toolName)
                        append(" (internal context; never quote or expose this wrapper):\n")
                        append(message.content)
                        append("\nContinue the original request. Do not answer until all requested actions are complete.")
                    }
                )
            }

            else -> message
        }
    }

    // Omitting tool-call assistant turns can leave adjacent user/result turns. Combining them is
    // accepted by more OpenAI-compatible upstreams and keeps the original request next to results.
    return flattened.fold(mutableListOf()) { result, message ->
        val previous = result.lastOrNull()
        if (previous?.role == "user" && message.role == "user" &&
            previous.imageDataUri == null && message.imageDataUri == null
        ) {
            result[result.lastIndex] = previous.copy(content = previous.content + "\n\n" + message.content)
        } else {
            result += message
        }
        result
    }
}

internal fun isCompatibilityTranscript(content: String): Boolean {
    val normalized = content.trimStart()
    return normalized.startsWith("[Tool calls completed]", ignoreCase = true) ||
        normalized.startsWith("The assistant requested these application tools:", ignoreCase = true) ||
        normalized.startsWith("APPLICATION TOOL RESULT from", ignoreCase = true)
}

internal fun Throwable.isHttp400(): Boolean =
    message?.startsWith("HTTP 400") == true
