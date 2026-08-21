package com.restaurant.pos.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.restaurant.pos.data.db.AppDatabase
import com.restaurant.pos.data.network.NetworkConnectivityObserver
import com.restaurant.pos.data.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoBackupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("pos_backup_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("auto_backup_enabled", false)

        if (!isEnabled) {
            // If user turned off auto backup, don't execute
            return@withContext Result.success()
        }

        try {
            val database = AppDatabase.getInstance(appContext)
            val backupManager = BackupManager(appContext, database)

            // 1. Local auto backup
            val localResult = backupManager.createAutoBackup()
            if (localResult is BackupResult.Error) {
                prefs.edit()
                    .putString("last_auto_backup_status", "FAILED")
                    .putString("last_auto_backup_error", localResult.message)
                    .apply()

                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            val localSuccess = localResult as BackupResult.Success

            // 2. Cloud Auto Backup (Firestore) - REQUIRED for overall Auto Backup success
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                val authErrorMsg = "Unauthenticated: User must be logged in to complete Cloud Auto Backup."
                prefs.edit()
                    .putString("last_auto_backup_status", "FAILED")
                    .putString("last_auto_backup_error", authErrorMsg)
                    .apply()

                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            val networkObserver = NetworkConnectivityObserver(appContext)
            val cloudSyncManager = CloudSyncManager(appContext, database, networkObserver)
            val cloudResult = cloudSyncManager.performManualBackup()

            if (cloudResult.isFailure) {
                val cloudErrorMsg = cloudResult.exceptionOrNull()?.message ?: "Cloud sync failed during auto-backup"
                Log.w("AutoBackupWorker", "Cloud sync encountered error during auto-backup: $cloudErrorMsg")
                prefs.edit()
                    .putString("last_auto_backup_status", "FAILED")
                    .putString("last_auto_backup_error", cloudErrorMsg)
                    .apply()

                return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            // Both Local Backup + Cloud Backup succeeded
            prefs.edit()
                .putLong("last_auto_backup_time", localSuccess.fileInfo.timestamp)
                .putString("last_auto_backup_status", "SUCCESS")
                .putString("last_auto_backup_error", null)
                .putString("last_auto_backup_file", localSuccess.fileInfo.fileName)
                .putString("last_auto_backup_summary", localSuccess.fileInfo.recordSummary)
                .apply()

            Log.d("AutoBackupWorker", "Auto backup (Local + Cloud Firestore) completed successfully.")
            Result.success()
        } catch (e: Exception) {
            prefs.edit()
                .putString("last_auto_backup_status", "FAILED")
                .putString("last_auto_backup_error", e.localizedMessage ?: "Unknown error")
                .apply()

            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "restaurant_pos_auto_backup"
        const val UNIQUE_ONE_TIME_WORK_NAME = "restaurant_pos_auto_backup_immediate"
    }
}
