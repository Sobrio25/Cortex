package com.aiagents.app.data.terminal

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiagents.app.data.local.MemoryDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryDao: MemoryDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "memory_maintenance"
        private const val TAG = "MemoryMaintenance"
        private const val MAX_MEMORIES = 500
        private const val MAX_SUMMARIES = 50
    }

    override suspend fun doWork(): Result {
        return try {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            memoryDao.decayOldMemories(threshold = thirtyDaysAgo, factor = 0.9f)
            memoryDao.deleteWeakMemories()
            memoryDao.deleteExpiredMemories()
            // Cap conversation summaries and total memories
            memoryDao.deleteOldestSummaries(MAX_SUMMARIES)
            val total = memoryDao.count()
            if (total > MAX_MEMORIES) {
                memoryDao.deleteLowestValue(total - MAX_MEMORIES)
            }
            Log.i(TAG, "Maintenance complete. Total memories: ${memoryDao.count()}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Maintenance failed", e)
            Result.retry()
        }
    }

}
