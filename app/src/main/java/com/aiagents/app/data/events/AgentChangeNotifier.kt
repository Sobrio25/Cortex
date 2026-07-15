package com.aiagents.app.data.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Confirmed, user-visible persistence changes made by an agent. */
enum class AgentChangeKind {
    MEMORY_SAVED,
    SKILL_CREATED,
    SKILL_UPDATED
}

data class AgentChangeEvent(
    val kind: AgentChangeKind,
    val title: String,
    val detail: String
)

/**
 * Process-local event stream for short-lived chat indicators.
 *
 * Events are intentionally not replayed: opening a chat must not show an old save as if it had
 * just happened. The buffer only protects bursts while the visible collector renders each event.
 */
@Singleton
class AgentChangeNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<AgentChangeEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AgentChangeEvent> = _events.asSharedFlow()

    fun memorySaved(target: String, itemCount: Int = 1) {
        val detail = when (target) {
            TARGET_USER -> "Perfil persistente · USER.md"
            TARGET_ARCHIVE -> if (itemCount == 1) {
                "1 dato en memoria secundaria"
            } else {
                "$itemCount datos en memoria secundaria"
            }
            else -> "Memoria activa · MEMORY.md"
        }
        _events.tryEmit(
            AgentChangeEvent(
                kind = AgentChangeKind.MEMORY_SAVED,
                title = "Memoria actualizada",
                detail = detail
            )
        )
    }

    fun skillCreated(name: String) {
        _events.tryEmit(
            AgentChangeEvent(
                kind = AgentChangeKind.SKILL_CREATED,
                title = "Skill creada",
                detail = name.trim().ifBlank { "Nueva skill" }
            )
        )
    }

    fun skillUpdated(name: String) {
        _events.tryEmit(
            AgentChangeEvent(
                kind = AgentChangeKind.SKILL_UPDATED,
                title = "Skill actualizada",
                detail = name.trim().ifBlank { "Skill existente" }
            )
        )
    }

    companion object {
        const val TARGET_MEMORY = "memory"
        const val TARGET_USER = "user"
        const val TARGET_ARCHIVE = "archive"
    }
}
