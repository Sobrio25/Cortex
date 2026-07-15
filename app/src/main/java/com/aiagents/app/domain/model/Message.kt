package com.aiagents.app.domain.model

import com.google.gson.annotations.SerializedName

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
    @SerializedName(value = "id", alternate = ["a"])
    val id: String,
    @SerializedName(value = "type", alternate = ["b"])
    val type: String = "function",
    @SerializedName(value = "function", alternate = ["c"])
    val function: ToolFunction,
    @SerializedName(value = "thoughtSignature", alternate = ["d"])
    val thoughtSignature: String? = null
)

data class ToolFunction(
    @SerializedName(value = "name", alternate = ["a"])
    val name: String,
    @SerializedName(value = "arguments", alternate = ["b"])
    val arguments: String
)

data class ToolResult(
    @SerializedName(value = "toolCallId", alternate = ["a"])
    val toolCallId: String,
    @SerializedName(value = "name", alternate = ["b"])
    val name: String,
    @SerializedName(value = "content", alternate = ["c"])
    val content: String
)

data class ChatContext(
    val agentId: Long,
    val messages: List<Message>,
    val systemPrompt: String
)
