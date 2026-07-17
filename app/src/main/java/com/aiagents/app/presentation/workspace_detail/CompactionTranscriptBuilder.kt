package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.domain.model.Message

/** Builds the bounded transcript passed to the context-checkpoint summarizer. */
internal object CompactionTranscriptBuilder {
    fun build(messages: List<Message>, tokenBudget: Int): String {
        val totalCharacterBudget = (tokenBudget * 3).coerceAtLeast(12_000)
        val perMessageCharacterLimit = (totalCharacterBudget / messages.size.coerceAtLeast(1))
            .coerceIn(128, 12_000)

        return buildString {
            messages.forEach { message ->
                val details = buildString {
                    append(message.content)
                    if (message.attachedFiles.isNotEmpty()) {
                        append("\nAttached files: ")
                        append(message.attachedFiles.joinToString(", "))
                    }
                    message.toolCalls.forEach { call ->
                        append("\nTool call ${call.function.name}: ${call.function.arguments}")
                    }
                    // TOOL messages normally persist their provider-visible content. Metadata is
                    // retained as a compatibility fallback for old or partial rows.
                    if (message.content.isBlank()) {
                        message.toolResults.forEach { result ->
                            append("\nTool result ${result.name}: ${result.content}")
                        }
                    }
                }.take(perMessageCharacterLimit)
                if (details.isNotBlank()) {
                    appendLine("[${message.role.name}]: $details")
                    appendLine()
                }
            }
        }.take(totalCharacterBudget)
    }
}
