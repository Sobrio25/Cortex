package com.aiagents.app.data.skills

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiagents.app.data.local.SkillReviewDao
import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.domain.model.SkillReviewStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Local-only worker: it has no HTTP client, shell executor, or tool dispatcher dependency. */
@HiltWorker
class SkillReviewWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reviewDao: SkillReviewDao,
    private val skillRepository: SkillRepository,
    private val backgroundSkillReviewer: BackgroundSkillReviewer
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val reviewId = inputData.getLong(KEY_REVIEW_ID, -1L)
        if (reviewId <= 0) return Result.failure()

        val review = reviewDao.getById(reviewId) ?: return Result.success()
        if (review.status != SkillReviewStatus.PENDING.name) return Result.success()

        return try {
            val candidate = backgroundSkillReviewer.review(review.redactedTranscript)
            if (candidate == null) {
                reviewDao.complete(
                    id = reviewId,
                    status = SkillReviewStatus.SKIPPED.name,
                    summary = "No se detectó un flujo repetible con suficiente confianza.",
                    candidateSkillId = null,
                    completedAt = System.currentTimeMillis()
                )
            } else {
                val skillId = skillRepository.createAutomaticDraft(candidate.draft).getOrThrow()
                reviewDao.complete(
                    id = reviewId,
                    status = SkillReviewStatus.DRAFT_CREATED.name,
                    summary = candidate.reason,
                    candidateSkillId = skillId,
                    completedAt = System.currentTimeMillis()
                )
            }
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                reviewDao.complete(
                    id = reviewId,
                    status = SkillReviewStatus.FAILED.name,
                    summary = error.message?.take(300) ?: "Error local al revisar el transcript.",
                    candidateSkillId = null,
                    completedAt = System.currentTimeMillis()
                )
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_REVIEW_ID = "review_id"
    }
}
