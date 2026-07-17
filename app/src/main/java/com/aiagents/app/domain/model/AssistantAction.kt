package com.aiagents.app.domain.model

enum class AssistantActionStatus {
    DRAFT,
    HANDED_OFF,
    CANCELLED,
    EXPIRED,
    FAILED
}

enum class ContactActionPurpose {
    CALL,
    WHATSAPP
}

data class AssistantContactCandidate(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val label: String? = null,
    val photoUri: String? = null
)

sealed interface PendingAssistantAction {
    val id: String
    val expiresAt: Long
    val status: AssistantActionStatus

    data class WhatsAppDraft(
        override val id: String,
        val contact: AssistantContactCandidate,
        val message: String,
        override val expiresAt: Long,
        override val status: AssistantActionStatus = AssistantActionStatus.DRAFT,
        val failureMessage: String? = null
    ) : PendingAssistantAction

    data class ContactSelection(
        override val id: String,
        val purpose: ContactActionPurpose,
        val query: String,
        val candidates: List<AssistantContactCandidate>,
        val message: String? = null,
        override val expiresAt: Long,
        override val status: AssistantActionStatus = AssistantActionStatus.DRAFT
    ) : PendingAssistantAction
}

data class AssistantActionResult(
    val success: Boolean,
    val message: String
)
