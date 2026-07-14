package com.aiagents.app.data.skills

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
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
 * Event-driven WorkManager bridge. The chat layer can call [recordMessage] after
 * persisting a user message; this module deliberately does not depend on it.
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
     * Returns true only when this call queued a review. System/tool messages are
     * never counted or persisted, and only user messages advance the interval.
     */
    suspend fun recordMessage(
        scopeId: Long?,
        message: Message,
        recentTranscript: List<Message> = emptyList()
    ): Boolean = recordMessage(
        scopeId = scopeId,
        message = message.toSkillTranscriptMessage(),
        recentTranscript = recentTranscript.map { it.toSkillTranscriptMessage() }
    )

    suspend fun recordMessage(
        scopeId: Long?,
        message: SkillTranscriptMessage,
        recentTranscript: List<SkillTranscriptMessage> = emptyList()
    ): Boolean = mutex.withLock {
        if (!_settings.value.enabled || message.role != SkillTranscriptRole.USER) return@withLock false

        val scopeKey = scopeId?.let { "workspace:$it" } ?: "global"
        val scopeHash = sha256("$installationSalt:$scopeKey").take(16)
        val counterKey = "$COUNTER_PREFIX$scopeHash"
        val nextCount = preferences.getInt(counterKey, 0) + 1
        val interval = _settings.value.messageInterval
        if (nextCount < interval) {
            preferences.edit().putInt(counterKey, nextCount).apply()
            return@withLock false
        }
        preferences.edit().putInt(counterKey, 0).apply()

        val boundedMessages = (recentTranscript + message)
            .fold(mutableListOf<SkillTranscriptMessage>()) { result, entry ->
                if (result.lastOrNull() != entry) result += entry
                result
            }
        val redacted = SkillTranscriptRedactor.redact(boundedMessages)
        if (redacted.isBlank()) return@withLock false

        val fingerprint = sha256("$scopeHash\n$redacted")
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

        val request = OneTimeWorkRequestBuilder<SkillReviewWorker>()
            .setInputData(workDataOf(SkillReviewWorker.KEY_REVIEW_ID to reviewId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
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
        const val DEFAULT_MESSAGE_INTERVAL = 20
        val ALLOWED_INTERVALS = setOf(10, 20, 40)
        const val WORK_TAG = "skill_review"
        private const val UNIQUE_WORK_PREFIX = "skill_review_"
        private const val PREFERENCES_NAME = "skill_review_scheduler"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "message_interval"
        private const val KEY_INSTALLATION_SALT = "installation_salt"
        private const val COUNTER_PREFIX = "message_count_"
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
