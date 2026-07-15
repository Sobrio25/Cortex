package com.aiagents.app.data.orchestration

import com.aiagents.app.data.remote.isCompatibilityTranscript
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole

internal object ToolCompletionPolicy {
    private val mutationRequest = Regex(
        """\b(actualiza(?:r|lo|la)?|modifica(?:r|lo|la)?|edita(?:r|lo|la)?|corrige(?:r|lo|la)?|""" +
            """cambia(?:r|lo|la)?|update|modify|edit|rewrite|write)\b""",
        RegexOption.IGNORE_CASE
    )
    private val workspaceArtifact = Regex(
        """\b(html?|archivo|documento|file|document|página|page)\b|\.[a-z0-9]{1,8}\b""",
        RegexOption.IGNORE_CASE
    )

    fun recoveryInstruction(messages: List<Message>, responseContent: String): String? {
        if (isCompatibilityTranscript(responseContent)) {
            return """
                INTERNAL COMPLETION CHECK: Your previous output exposed an application tool transcript.
                Never quote tool calls, wrappers, URLs-as-a-call-list, or internal protocol. Continue the
                original task now and return only the completed user-facing result. If the request changes
                a workspace file, perform the write before answering.
            """.trimIndent()
        }

        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex < 0) return null
        val request = messages[lastUserIndex].content
        if (!mutationRequest.containsMatchIn(request) || !workspaceArtifact.containsMatchIn(request)) {
            return null
        }

        val laterMessages = messages.drop(lastUserIndex + 1)
        val writeCallIds = laterMessages
            .flatMap(Message::toolCalls)
            .filter { it.function.name == "write_file" }
            .mapTo(mutableSetOf()) { it.id }
        val successfulWrite = laterMessages
            .flatMap(Message::toolResults)
            .any { result ->
                result.toolCallId in writeCallIds &&
                    !result.content.trimStart().startsWith("Error", ignoreCase = true)
            }
        if (successfulWrite) return null

        return """
            INTERNAL COMPLETION CHECK: The original request requires updating a workspace file, but no
            successful write_file result exists yet. Do not provide a final answer or describe intended
            work. Call write_file with the complete updated content, then verify the file and only afterward
            return one concise user-facing completion summary.
        """.trimIndent()
    }
}
