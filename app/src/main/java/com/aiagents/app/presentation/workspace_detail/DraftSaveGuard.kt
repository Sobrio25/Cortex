package com.aiagents.app.presentation.workspace_detail

/** Identity of a draft editor. Drafts from different conversations never share a scope. */
data class DraftScope(val workspaceId: Long, val conversationId: Long?)

data class DraftSaveToken internal constructor(
    internal val generation: Long,
    internal val scope: DraftScope,
    internal val text: String
)

/** Rejects delayed writes created before a send or conversation switch. */
class DraftSaveGuard {
    private var generation: Long = 0

    fun capture(scope: DraftScope, text: String): DraftSaveToken =
        DraftSaveToken(generation, scope, text)

    fun invalidate() {
        generation++
    }

    fun canPersist(token: DraftSaveToken, currentScope: DraftScope, currentText: String): Boolean =
        token.generation == generation && token.scope == currentScope && token.text == currentText
}
