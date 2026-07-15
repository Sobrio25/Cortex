package com.aiagents.app.data.skills

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiagents.app.data.events.AgentChangeNotifier
import com.aiagents.app.data.local.SkillReviewDao
import com.aiagents.app.data.memory.CortexMarkdownMemoryStore
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.domain.model.SkillReviewStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Applies only the validated, declarative changes returned by the isolated reviewer. */
@HiltWorker
class SkillReviewWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reviewDao: SkillReviewDao,
    private val skillRepository: SkillRepository,
    private val memoryStore: CortexMarkdownMemoryStore,
    private val profileStore: CortexProfileStore,
    private val backgroundSkillReviewer: BackgroundSkillReviewer,
    private val changeNotifier: AgentChangeNotifier
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reviewId = inputData.getLong(KEY_REVIEW_ID, -1L)
        if (reviewId <= 0) return Result.failure()

        val review = reviewDao.getById(reviewId) ?: return Result.success()
        if (review.status != SkillReviewStatus.PENDING.name) return Result.success()

        return try {
            val reviewMemory = inputData.getBoolean(KEY_REVIEW_MEMORY, false)
            val reviewSkills = inputData.getBoolean(KEY_REVIEW_SKILLS, false)
            val outcome = backgroundSkillReviewer.review(
                BackgroundReviewRequest(
                    redactedTranscript = review.redactedTranscript,
                    modelKey = inputData.getString(KEY_MODEL_KEY).orEmpty(),
                    reviewMemory = reviewMemory,
                    reviewSkills = reviewSkills,
                    memoryContent = memoryStore.snapshot().content,
                    userContent = profileStore.userSnapshot().content,
                    existingSkills = skillRepository.getSkillsOnce()
                )
            )

            if (outcome == null) {
                completeSkipped(reviewId, "No había un modelo disponible ni aprendizaje seguro que guardar.")
                return Result.success()
            }

            var changed = false
            var candidateSkillId: Long? = null
            val notes = mutableListOf<String>()

            if (reviewMemory) {
                val memoryOperations = outcome.memoryOperations
                    .filter { it.target == BackgroundMemoryTarget.MEMORY }
                    .map { it.operation }
                val userOperations = outcome.memoryOperations
                    .filter { it.target == BackgroundMemoryTarget.USER }
                    .map { it.operation }

                if (memoryOperations.isNotEmpty()) {
                    val result = memoryStore.applyOperations(memoryOperations)
                    changed = changed || (result.success && result.changed)
                    if (result.success && result.changed) {
                        changeNotifier.memorySaved(AgentChangeNotifier.TARGET_MEMORY)
                    }
                    if (!result.success) notes += "MEMORY.md: ${result.message}"
                }
                if (userOperations.isNotEmpty()) {
                    val result = profileStore.applyUserOperations(userOperations)
                    changed = changed || (result.success && result.changed)
                    if (result.success && result.changed) {
                        changeNotifier.memorySaved(AgentChangeNotifier.TARGET_USER)
                    }
                    if (!result.success) notes += "USER.md: ${result.message}"
                }
            }

            if (reviewSkills) {
                outcome.skillChange?.let { change ->
                    val previousSkillIds = skillRepository.getSkillsOnce().mapTo(mutableSetOf()) { it.id }
                    val result = when (change.action) {
                        BackgroundSkillAction.CREATE -> skillRepository.createAutomaticActive(change.draft)
                        BackgroundSkillAction.UPDATE -> skillRepository.updateAutomatic(
                            id = requireNotNull(change.existingSkillId),
                            input = change.draft
                        )
                    }
                    result.onSuccess { skillId ->
                        candidateSkillId = skillId
                        changed = true
                        if (skillId in previousSkillIds) {
                            changeNotifier.skillUpdated(change.draft.name)
                        } else {
                            changeNotifier.skillCreated(change.draft.name)
                        }
                    }.onFailure { error ->
                        notes += error.message ?: "La skill automática fue rechazada."
                    }
                }
            }

            val summary = buildList {
                outcome.summary.takeIf(String::isNotBlank)?.let(::add)
                addAll(notes)
            }.joinToString(" ").take(600)

            reviewDao.complete(
                id = reviewId,
                status = if (changed) SkillReviewStatus.CHANGES_APPLIED.name else SkillReviewStatus.SKIPPED.name,
                summary = summary.ifBlank {
                    if (changed) "Memoria o skills actualizadas automáticamente."
                    else "No se encontró aprendizaje duradero que guardar."
                },
                candidateSkillId = candidateSkillId,
                completedAt = System.currentTimeMillis()
            )
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                reviewDao.complete(
                    id = reviewId,
                    status = SkillReviewStatus.FAILED.name,
                    summary = error.message?.take(300) ?: "Error al revisar el aprendizaje del turno.",
                    candidateSkillId = null,
                    completedAt = System.currentTimeMillis()
                )
                Result.failure()
            }
        }
    }

    private suspend fun completeSkipped(reviewId: Long, summary: String) {
        reviewDao.complete(
            id = reviewId,
            status = SkillReviewStatus.SKIPPED.name,
            summary = summary,
            candidateSkillId = null,
            completedAt = System.currentTimeMillis()
        )
    }

    companion object {
        const val KEY_REVIEW_ID = "review_id"
        const val KEY_REVIEW_MEMORY = "review_memory"
        const val KEY_REVIEW_SKILLS = "review_skills"
        const val KEY_MODEL_KEY = "model_key"
    }
}
