package com.screentime.tracker.service

import android.content.Context
import androidx.work.*
import com.screentime.tracker.data.UsageRepository
import java.util.concurrent.TimeUnit

class UsageCollectorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = UsageRepository(applicationContext)
            if (repo.hasUsagePermission()) {
                repo.collectAndSaveToday()
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "screen_time_collector"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)  // run even on low battery
                .build()

            // Run every 30 minutes — good balance of accuracy vs battery
            val request = PeriodicWorkRequestBuilder<UsageCollectorWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageCollectorWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
