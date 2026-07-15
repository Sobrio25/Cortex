package com.aiagents.app.data.skills

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aiagents.app.data.local.SkillReviewDao
import com.aiagents.app.data.model.SkillReviewEntity
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.SkillReviewStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SkillReviewSettings(
    val enabled: Boolean = true,
    val messageInterval: Int = SkillReviewScheduler.DEFAULT_MESSAGE_INTERVAL
)

/**
 * Event-driven WorkManager bridge inspired by Hermes' post-turn background review.
 * Only a bounded, redacted transcript is ever stored or passed to the worker.
 */
@Singleton
class SkillReviewScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reviewDao: SkillReviewDao
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val installationSalt = preferences.getString(KEY_INSTALLATION_SALT, null)
        ?: UUID.randomUUID().toString().also { salt ->
            preferences.edit().putString(KEY_INSTALLATION_SALT, salt).commit()
        }
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SkillReviewSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    fun setMessageInterval(interval: Int) {
        require(interval in ALLOWED_INTERVALS) { "Intervalo de revisión no permitido" }
        preferences.edit().putInt(KEY_INTERVAL, interval).apply()
        _settings.value = _settings.value.copy(messageInterval = interval)
    }

    /**
     * Called only after Cortex persisted its final assistant response. Memory advances once per
     * completed user turn; skill learning advances once per model iteration in that turn.
     */
    suspend fun recordCompletedTurn(
        scopeId: Long?,
        recentTranscript: List<Message>,
        modelKey: String
    ): Boolean = recordCompletedTranscript(
        scopeId = scopeId,
        recentTranscript = recentTranscript.map { it.toSkillTranscriptMessage() },
        modelKey = modelKey
    )

    private suspend fun recordCompletedTranscript(
        scopeId: Long?,
        recentTranscript: List<SkillTranscriptMessage>,
        modelKey: String
    ): Boolean = mutex.withLock {
        if (!_settings.value.enabled) return@withLock false

        val lastUserIndex = recentTranscript.indexOfLast { it.role == SkillTranscriptRole.USER }
        if (lastUserIndex < 0) return@withLock false
        val completedTurnIterations = recentTranscript
            .drop(lastUserIndex + 1)
            .count { it.role == SkillTranscriptRole.ASSISTANT }
        if (completedTurnIterations <= 0) return@withLock false

        val scopeKey = scopeId?.let { "workspace:$it" } ?: "global"
        val scopeHash = sha256("$installationSalt:$scopeKey").take(16)
        val memoryCounterKey = "$MEMORY_COUNTER_PREFIX$scopeHash"
        val skillCounterKey = "$SKILL_COUNTER_PREFIX$scopeHash"
        val interval = _settings.value.messageInterval
        val cadence = SelfImprovementCadence.advance(
            current = SelfImprovementCadenceState(
                memoryTurns = preferences.getInt(memoryCounterKey, 0),
                skillIterations = preferences.getInt(skillCounterKey, 0)
            ),
            completedTurnIterations = completedTurnIterations,
            interval = interval
        )
        preferences.edit()
            .putInt(memoryCounterKey, cadence.state.memoryTurns)
            .putInt(skillCounterKey, cadence.state.skillIterations)
            .apply()
        if (!cadence.reviewMemory && !cadence.reviewSkills) {
            return@withLock false
        }

        val redacted = SkillTranscriptRedactor.redact(recentTranscript)
        if (redacted.isBlank()) return@withLock false

        val fingerprint = sha256(
            "$scopeHash:${cadence.reviewMemory}:${cadence.reviewSkills}\n$redacted"
        )
        val reviewId = reviewDao.insert(
            SkillReviewEntity(
                scopeHash = scopeHash,
                messageCount = interval,
                transcriptFingerprint = fingerprint,
                redactedTranscript = redacted,
                status = SkillReviewStatus.PENDING.name,
                createdAt = System.currentTimeMillis()
            )
        )
        if (reviewId <= 0) return@withLock false

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .apply {
                if (modelKey.isNotBlank() && !modelKey.startsWith("LOCAL|", ignoreCase = true)) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
            }
            .build()
        val request = OneTimeWorkRequestBuilder<SkillReviewWorker>()
            .setInputData(
                workDataOf(
                    SkillReviewWorker.KEY_REVIEW_ID to reviewId,
                    SkillReviewWorker.KEY_REVIEW_MEMORY to cadence.reviewMemory,
                    SkillReviewWorker.KEY_REVIEW_SKILLS to cadence.reviewSkills,
                    SkillReviewWorker.KEY_MODEL_KEY to modelKey
                )
            )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$UNIQUE_WORK_PREFIX$reviewId",
            ExistingWorkPolicy.KEEP,
            request
        )
        true
    }

    private fun readSettings(): SkillReviewSettings {
        val interval = preferences.getInt(KEY_INTERVAL, DEFAULT_MESSAGE_INTERVAL)
            .takeIf { it in ALLOWED_INTERVALS }
            ?: DEFAULT_MESSAGE_INTERVAL
        return SkillReviewSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            messageInterval = interval
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_MESSAGE_INTERVAL = 10
        val ALLOWED_INTERVALS = setOf(10, 20, 40)
        const val WORK_TAG = "skill_review"
        private const val UNIQUE_WORK_PREFIX = "skill_review_"
        private const val PREFERENCES_NAME = "skill_review_scheduler"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "message_interval"
        private const val KEY_INSTALLATION_SALT = "installation_salt"
        private const val MEMORY_COUNTER_PREFIX = "memory_turn_count_"
        private const val SKILL_COUNTER_PREFIX = "skill_iteration_count_"
    }
}

private fun Message.toSkillTranscriptMessage(): SkillTranscriptMessage = SkillTranscriptMessage(
    role = when (role) {
        MessageRole.USER -> SkillTranscriptRole.USER
        MessageRole.ASSISTANT -> SkillTranscriptRole.ASSISTANT
        MessageRole.SYSTEM -> SkillTranscriptRole.SYSTEM
        MessageRole.TOOL -> SkillTranscriptRole.TOOL
    },
    content = content
)
