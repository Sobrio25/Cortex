package com.aiagents.app.data.local

import kotlin.math.max

internal data class LocalModelMemoryDecision(
    val allowed: Boolean,
    val estimatedPeakBytes: Long,
    val safeBudgetBytes: Long
)

/**
 * Conservative admission policy for on-device models.
 *
 * Model files are memory-mapped, but inference also needs KV cache, native buffers,
 * Compose/app memory and enough headroom for Android itself. Allowing a foreground
 * process to consume most of a 6 GB phone causes LMK to terminate it without a Java
 * exception, so large models are rejected before their native engine is initialized.
 */
internal object LocalModelMemoryPolicy {
    private const val MEBIBYTE = 1024L * 1024L
    private const val FIXED_RUNTIME_HEADROOM_BYTES = 384L * MEBIBYTE
    private const val ANDROID_RESERVE_BYTES = 512L * MEBIBYTE
    private const val MAX_TOTAL_MEMORY_FRACTION = 0.32

    fun evaluate(
        modelBytes: Long,
        totalMemoryBytes: Long,
        availableMemoryBytes: Long
    ): LocalModelMemoryDecision {
        if (modelBytes <= 0L || totalMemoryBytes <= 0L || availableMemoryBytes <= 0L) {
            return LocalModelMemoryDecision(
                allowed = true,
                estimatedPeakBytes = modelBytes.coerceAtLeast(0L),
                safeBudgetBytes = availableMemoryBytes.coerceAtLeast(0L)
            )
        }

        val runtimeHeadroom = max(FIXED_RUNTIME_HEADROOM_BYTES, modelBytes / 5L)
        val estimatedPeak = modelBytes + runtimeHeadroom
        val totalMemoryBudget = (totalMemoryBytes * MAX_TOTAL_MEMORY_FRACTION).toLong()
        val availableMemoryBudget = (availableMemoryBytes - ANDROID_RESERVE_BYTES).coerceAtLeast(0L)
        val safeBudget = minOf(totalMemoryBudget, availableMemoryBudget)

        return LocalModelMemoryDecision(
            allowed = estimatedPeak <= safeBudget,
            estimatedPeakBytes = estimatedPeak,
            safeBudgetBytes = safeBudget
        )
    }
}
