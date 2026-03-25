package com.aiagents.app.domain.model

data class Message(
    val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFiles: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val reasoning: String? = null,
    val imageDataUris: List<String> = emptyList(),  // Para enviar imágenes como contenido vision
    val subConversationId: Long? = null  // enlaza un mensaje-resumen a su sub-conversación
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolFunction,
    val thoughtSignature: String? = null
)

data class ToolFunction(
    val name: String,
    val arguments: String
)

data class ToolResult(
    val toolCallId: String,
    val name: String,
    val content: String
)

data class ChatContext(
    val agentId: Long,
    val messages: List<Message>,
    val systemPrompt: String
)
