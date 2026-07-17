package com.aiagents.app.data.diagnostics

import android.util.Log
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticTraceStatus {
    REQUESTING,
    AWAITING_TOOLS,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DiagnosticToolPhase { CALL, RESULT }

/** Metadata-only event. Tool arguments and result content are intentionally never retained. */
data class DiagnosticToolEvent(
    val callId: String,
    val name: String,
    val phase: DiagnosticToolPhase
)

/**
 * A privacy-safe view of one user turn. This type must not grow fields containing prompts,
 * responses, tool arguments, headers, URLs, credentials, or raw exception messages.
 */
data class DiagnosticTurnTrace(
    val id: String,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val provider: String,
    val model: String,
    val agent: String,
    val requestCount: Int,
    val messageCount: Int,
    val exposedToolCount: Int,
    val providerDurationMs: Long,
    val status: DiagnosticTraceStatus,
    val toolEvents: List<DiagnosticToolEvent>,
    val errorCategory: String? = null
)

/** Stable across every provider/tool round belonging to the same last user message. */
object TurnCorrelationId {
    fun from(agentId: Long, messages: List<Message>): String {
        val user = messages.lastOrNull { it.role == MessageRole.USER }
        val seed = "$agentId:${user?.id ?: 0L}:${user?.timestamp ?: 0L}"
        val uuid = UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
        return "turn-${uuid.toString().take(18)}"
    }
}

/**
 * In-process, bounded diagnostic ring buffer. It deliberately does not persist across process
 * restarts so sensitive operational history has the shortest useful lifetime.
 */
@Singleton
class DiagnosticTraceStore private constructor(
    private val capacity: Int
) {
    @Inject
    constructor() : this(DEFAULT_CAPACITY)

    private val lock = Any()
    private val mutableTraces = MutableStateFlow<List<DiagnosticTurnTrace>>(emptyList())
    val traces: StateFlow<List<DiagnosticTurnTrace>> = mutableTraces.asStateFlow()

    init {
        require(capacity > 0) { "Diagnostic capacity must be positive" }
    }

    fun beginProviderRequest(
        traceId: String,
        provider: String,
        model: String,
        agent: String,
        messageCount: Int,
        exposedToolCount: Int,
        existingToolResults: List<ToolResult> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        mutate(traceId) { current ->
            val resultEvents = existingToolResults.map { result ->
                DiagnosticToolEvent(
                    callId = safeIdentifier(result.toolCallId),
                    name = safeIdentifier(result.name),
                    phase = DiagnosticToolPhase.RESULT
                )
            }
            (current ?: DiagnosticTurnTrace(
                id = safeIdentifier(traceId),
                startedAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
                provider = safeIdentifier(provider),
                model = safeIdentifier(model),
                agent = safeIdentifier(agent),
                requestCount = 0,
                messageCount = messageCount,
                exposedToolCount = exposedToolCount,
                providerDurationMs = 0,
                status = DiagnosticTraceStatus.REQUESTING,
                toolEvents = emptyList()
            )).copy(
                updatedAtEpochMs = nowEpochMs,
                provider = safeIdentifier(provider),
                model = safeIdentifier(model),
                agent = safeIdentifier(agent),
                requestCount = (current?.requestCount ?: 0) + 1,
                messageCount = messageCount,
                exposedToolCount = exposedToolCount,
                status = DiagnosticTraceStatus.REQUESTING,
                toolEvents = mergeEvents(current?.toolEvents.orEmpty(), resultEvents),
                errorCategory = null
            )
        }
    }

    fun completeProviderRequest(
        traceId: String,
        durationMs: Long,
        toolCalls: List<ToolCall>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        mutate(traceId) { current ->
            val base = current ?: return@mutate null
            val callEvents = toolCalls.map { call ->
                DiagnosticToolEvent(
                    callId = safeIdentifier(call.id),
                    name = safeIdentifier(call.function.name),
                    phase = DiagnosticToolPhase.CALL
                )
            }
            base.copy(
                updatedAtEpochMs = nowEpochMs,
                providerDurationMs = base.providerDurationMs + durationMs.coerceAtLeast(0),
                status = if (toolCalls.isEmpty()) {
                    DiagnosticTraceStatus.COMPLETED
                } else {
                    DiagnosticTraceStatus.AWAITING_TOOLS
                },
                toolEvents = mergeEvents(base.toolEvents, callEvents),
                errorCategory = null
            )
        }
    }

    /** Records tool completion without retaining result content. Matches the originating turn by call id. */
    fun recordToolResults(
        results: List<ToolResult>,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        if (results.isEmpty()) return
        val changed = synchronized(lock) {
            val byCallId = results.associateBy { safeIdentifier(it.toolCallId) }
            val before = mutableTraces.value
            val updated = before.map { trace ->
                val matching = trace.toolEvents
                    .filter { it.phase == DiagnosticToolPhase.CALL && it.callId in byCallId }
                    .mapNotNull { call -> byCallId[call.callId] }
                if (matching.isEmpty()) return@map trace
                val mergedEvents = mergeEvents(
                    trace.toolEvents,
                    matching.map { result ->
                        DiagnosticToolEvent(
                            callId = safeIdentifier(result.toolCallId),
                            name = safeIdentifier(result.name),
                            phase = DiagnosticToolPhase.RESULT
                        )
                    }
                )
                val callIds = mergedEvents
                    .filter { it.phase == DiagnosticToolPhase.CALL }
                    .mapTo(mutableSetOf()) { it.callId }
                val resultIds = mergedEvents
                    .filter { it.phase == DiagnosticToolPhase.RESULT }
                    .mapTo(mutableSetOf()) { it.callId }
                trace.copy(
                    updatedAtEpochMs = nowEpochMs,
                    status = if (
                        trace.status == DiagnosticTraceStatus.AWAITING_TOOLS &&
                        callIds.all { it in resultIds }
                    ) {
                        DiagnosticTraceStatus.COMPLETED
                    } else {
                        trace.status
                    },
                    toolEvents = mergedEvents
                )
            }
            mutableTraces.value = updated
            updated.filterIndexed { index, trace -> trace != before.getOrNull(index) }
        }
        changed.forEach { structuredLog("tool_result_recorded", it) }
    }

    fun failProviderRequest(
        traceId: String,
        durationMs: Long,
        throwable: Throwable? = null,
        cancelled: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        mutate(traceId) { current ->
            current?.copy(
                updatedAtEpochMs = nowEpochMs,
                providerDurationMs = current.providerDurationMs + durationMs.coerceAtLeast(0),
                status = if (cancelled) DiagnosticTraceStatus.CANCELLED else DiagnosticTraceStatus.FAILED,
                errorCategory = if (cancelled) "CANCELLED" else categorize(throwable)
            )
        }
    }

    fun clear() {
        synchronized(lock) { mutableTraces.value = emptyList() }
        structuredLog("diagnostics_cleared", null)
    }

    private fun mutate(
        traceId: String,
        transform: (DiagnosticTurnTrace?) -> DiagnosticTurnTrace?
    ) {
        val updated = synchronized(lock) {
            val traces = mutableTraces.value
            val current = traces.firstOrNull { it.id == traceId }
            val next = transform(current)
            val withoutCurrent = traces.filterNot { it.id == traceId }
            val result = if (next == null) withoutCurrent else listOf(next) + withoutCurrent
            mutableTraces.value = result.take(capacity)
            next
        }
        structuredLog("turn_trace_updated", updated)
    }

    private fun structuredLog(event: String, trace: DiagnosticTurnTrace?) {
        val fields = linkedMapOf<String, Any?>(
            "event" to event,
            "traceId" to trace?.id,
            "provider" to trace?.provider,
            "model" to trace?.model,
            "requestCount" to trace?.requestCount,
            "durationMs" to trace?.providerDurationMs,
            "status" to trace?.status?.name,
            "toolCount" to trace?.toolEvents?.count { it.phase == DiagnosticToolPhase.CALL },
            "errorCategory" to trace?.errorCategory
        )
        val safeJson = SecureDiagnosticRedactor.redact(Gson().toJson(fields))
        // Local JVM tests do not provide android.util.Log; diagnostics must never break a turn.
        runCatching { Log.d(LOG_TAG, safeJson) }
    }

    companion object {
        const val DEFAULT_CAPACITY = 50

        fun forTesting(capacity: Int): DiagnosticTraceStore = DiagnosticTraceStore(capacity)

        private const val LOG_TAG = "CortexDiagnostics"

        private fun mergeEvents(
            existing: List<DiagnosticToolEvent>,
            incoming: List<DiagnosticToolEvent>
        ): List<DiagnosticToolEvent> = (existing + incoming).distinctBy {
            Triple(it.callId, it.name, it.phase)
        }

        private fun safeIdentifier(value: String): String =
            SecureDiagnosticRedactor.redact(value).take(160)

        private fun categorize(error: Throwable?): String {
            val value = error?.message.orEmpty().lowercase()
            return when {
                error is java.util.concurrent.CancellationException -> "CANCELLED"
                "401" in value || "403" in value || "unauthorized" in value -> "AUTH"
                "429" in value || "rate limit" in value -> "RATE_LIMIT"
                "timeout" in value || "timed out" in value -> "TIMEOUT"
                "network" in value || "unable to resolve host" in value -> "NETWORK"
                else -> "PROVIDER"
            }
        }
    }
}
