package com.restaurant.pos.data.syncv3

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.restaurant.pos.data.db.AppDatabase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeout

/**
 * Worker managed by Android WorkManager to process the offline sync queue
 * automatically when constraints (such as network availability) are satisfied.
 */
class SyncQueueWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Sync queue processing triggered by WorkManager.")
        android.util.Log.i(TAG, "WORKER_STARTED")

        val sharedPrefs = applicationContext.getSharedPreferences("pos_sync_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("sync_active", true)
            .putString("sync_error", null)
            .apply()

        // Validate Firebase Authentication state dynamically
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "Sync aborted: No authenticated Firebase user session found.")
            android.util.Log.i(TAG, "WORKER_RESULT = FAILURE")
            sharedPrefs.edit()
                .putBoolean("sync_active", false)
                .putString("sync_error", "Not authenticated with Firebase.")
                .apply()
            // Return failure to reschedule when appropriate (safely retrying later)
            return Result.failure()
        }

        android.util.Log.i(TAG, "WORKER_AUTH_OK")
        val authUid = currentUser.uid
        Log.i(TAG, "Dynamic identity authorized for sync execution: Account UID = $authUid")

        val database = AppDatabase.getInstance(applicationContext)
        val syncRecordDao = database.syncRecordDao()

        val queueSize = try {
            syncRecordDao.getPendingSyncRecords().size
        } catch (e: Exception) {
            0
        }
        android.util.Log.i(TAG, "QUEUE_SIZE = $queueSize")

        try {
            val repository = RealtimeSyncRepository(applicationContext, database)
            android.util.Log.i(TAG, "UPLOAD_STARTED")
            
            // Enforce a strict 30-second timeout for the entire sync queue upload operation
            val successCount = withTimeout(30000L) {
                repository.uploadPendingQueue()
            }
            
            Log.i(TAG, "Sync queue processing completed. Uploaded $successCount records successfully.")
            android.util.Log.i(TAG, "UPLOAD_SUCCESS")
            android.util.Log.i(TAG, "WORKER_RESULT = SUCCESS")

            sharedPrefs.edit()
                .putBoolean("sync_active", false)
                .putString("sync_error", null)
                .apply()

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure during queue synchronization execution.", e)
            android.util.Log.i(TAG, "UPLOAD_FAILED")
            android.util.Log.i(TAG, "WORKER_RESULT = RETRY")

            val msg = e.message ?: ""
            val errorMsg = when {
                e is kotlinx.coroutines.TimeoutCancellationException ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) -> {
                    "Network timeout during upload. Sync will retry automatically."
                }
                msg.contains("permission_denied", ignoreCase = true) ||
                msg.contains("permission denied", ignoreCase = true) ||
                msg.contains("denied", ignoreCase = true) -> {
                    "Permission denied: Database security rules violation."
                }
                else -> {
                    e.message ?: "Unknown error"
                }
            }

            sharedPrefs.edit()
                .putBoolean("sync_active", false)
                .putString("sync_error", errorMsg)
                .apply()

            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncQueueWorker"
        private const val WORK_NAME = "com.restaurant.pos.data.syncv3.SyncQueueWorker"

        /**
         * Enqueues or schedules a unique periodic work task to handle network-restoration sync triggers.
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncQueueWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "Enqueued periodic SyncQueueWorker task with CONNECTED network constraints.")
        }

        /**
         * Triggers a fast, one-time sync task immediately when network becomes available.
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncQueueWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_one_time",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            Log.d(TAG, "Triggered immediate, one-shot SyncQueueWorker task.")
        }
    }
}
