package com.aiagents.app.data.terminal

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aiagents.app.domain.model.ProviderType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Nudge de memoria en background: cuando el usuario abandona una conversación,
 * se encola este worker para que extraiga (vía LLM) los datos durables de los
 * mensajes nuevos y los persista en cortex_memories, sin depender de que la
 * Activity siga viva. Es el complemento "al cerrar sesión" de la extracción
 * post-turno y al reanudar que ya hace MemoryExtractor en foreground.
 */
@HiltWorker
class MemoryNudgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryExtractor: MemoryExtractor
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val conversationId = inputData.getLong(KEY_CONVERSATION_ID, -1L)
        val model = inputData.getString(KEY_MODEL).orEmpty()
        val provider = runCatching {
            ProviderType.valueOf(inputData.getString(KEY_PROVIDER).orEmpty())
        }.getOrNull()

        if (conversationId <= 0 || model.isBlank() || provider == null) {
            Log.w(TAG, "Memory nudge skipped: invalid input data")
            return Result.failure()
        }

        return try {
            memoryExtractor.checkConversationOnResume(conversationId, model, provider)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Memory nudge failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MemoryNudgeWorker"
        const val WORK_TAG = "memory_nudge"
        const val UNIQUE_WORK_NAME = "memory_nudge"

        private const val KEY_CONVERSATION_ID = "conversation_id"
        private const val KEY_MODEL = "model"
        private const val KEY_PROVIDER = "provider"

        /**
         * Encola el nudge para la conversación que se está abandonando.
         * Se usa REPLACE: solo interesa la conversación más reciente.
         */
        fun enqueue(
            workManager: WorkManager,
            conversationId: Long,
            model: String,
            provider: ProviderType
        ) {
            val data = workDataOf(
                KEY_CONVERSATION_ID to conversationId,
                KEY_MODEL to model,
                KEY_PROVIDER to provider.name
            )
            val constraints = if (provider == ProviderType.LOCAL) {
                Constraints.NONE
            } else {
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            }
            val request: OneTimeWorkRequest =
                OneTimeWorkRequestBuilder<MemoryNudgeWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
