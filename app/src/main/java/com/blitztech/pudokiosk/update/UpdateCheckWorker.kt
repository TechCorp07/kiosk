package com.blitztech.pudokiosk.update

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks for app updates.
 * Runs every [UpdateConfig.CHECK_INTERVAL_HOURS] hours when network is available.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"

        /**
         * Schedule periodic update checks.
         * Call once at app startup — WorkManager handles deduplication.
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                    UpdateConfig.CHECK_INTERVAL_HOURS, TimeUnit.HOURS,
                    UpdateConfig.FLEX_INTERVAL_HOURS, TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .addTag(UpdateConfig.UPDATE_WORK_TAG)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30, TimeUnit.MINUTES
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UpdateConfig.UPDATE_WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

                Log.d(TAG, "Update check worker scheduled (every ${UpdateConfig.CHECK_INTERVAL_HOURS}h)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule update check worker", e)
            }
        }

        /**
         * Trigger an immediate one-time update check (e.g. from technician menu).
         */
        fun checkNow(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setConstraints(constraints)
                    .addTag("${UpdateConfig.UPDATE_WORK_TAG}_manual")
                    .build()

                WorkManager.getInstance(context).enqueue(request)
                Log.d(TAG, "Manual update check enqueued")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue manual update check", e)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting update check...")

            val updateInfo = AppUpdateManager.checkForUpdate(applicationContext)

            if (updateInfo != null) {
                Log.d(TAG, "Update found: ${updateInfo.versionName} — downloading...")
                val success = AppUpdateManager.downloadAndInstall(applicationContext, updateInfo)

                if (success) {
                    Log.d(TAG, "Update download/install triggered successfully")
                    Result.success()
                } else {
                    Log.w(TAG, "Update download/install failed — will retry")
                    Result.retry()
                }
            } else {
                Log.d(TAG, "No update available")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.retry()
        }
    }
}
