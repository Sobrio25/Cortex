package com.aiagents.app.data.repository

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole

/**
 * Separates the durable transcript shown to the user from the reduced history sent to an LLM.
 * Original messages are never removed by compaction.
 */
object ContextCompactionPolicy {
    const val CHECKPOINT_PREFIX = "[CORTEX_CONTEXT_CHECKPOINT_V1]"
    private const val LEGACY_CHECKPOINT_PREFIX = "[Context Compacted]"

    fun checkpointContent(summary: String): String =
        "$CHECKPOINT_PREFIX\nPrevious conversation checkpoint:\n\n${summary.trim()}"

    fun isInternalCheckpoint(message: Message): Boolean =
        message.role == MessageRole.SYSTEM && message.content.startsWith(CHECKPOINT_PREFIX)

    fun isAnyCheckpoint(message: Message): Boolean =
        isInternalCheckpoint(message) ||
            (message.role == MessageRole.SYSTEM && message.content.startsWith(LEGACY_CHECKPOINT_PREFIX))

    /**
     * User-facing transcript.
     *
     * Assistant records that contain tool calls and TOOL responses are protocol turns, not
     * completed replies. They stay persisted for provider context but are removed from the
     * normal chat list so hidden rows cannot accumulate spacing between visible messages.
     * They remain available in the explicit command/debug view.
     */
    fun visibleHistory(
        messages: List<Message>,
        includeInternalActions: Boolean = false
    ): List<Message> = messages.filterNot { message ->
        isInternalCheckpoint(message) ||
            (!includeInternalActions &&
                (message.role == MessageRole.TOOL ||
                    (message.role == MessageRole.ASSISTANT && message.toolCalls.isNotEmpty())))
    }

    /** Latest checkpoint plus the verbatim tail that followed it. */
    fun modelHistory(messages: List<Message>): List<Message> {
        val checkpointIndex = messages.indexOfLast(::isAnyCheckpoint)
        if (checkpointIndex < 0) return messages
        return buildList {
            // Provider message APIs consistently accept user/assistant roles, while
            // some (notably Anthropic) reject a system role inside message history.
            add(messages[checkpointIndex].copy(role = MessageRole.USER))
            messages.drop(checkpointIndex + 1)
                .filterNot(::isInternalCheckpoint)
                .forEach(::add)
        }
    }
}
