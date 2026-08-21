package com.restaurant.pos.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.restaurant.pos.data.db.AppDatabase
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

            val result = backupManager.createAutoBackup()

            when (result) {
                is BackupResult.Success -> {
                    // Update prefs only after SUCCESS
                    prefs.edit()
                        .putLong("last_auto_backup_time", result.fileInfo.timestamp)
                        .putString("last_auto_backup_status", "SUCCESS")
                        .putString("last_auto_backup_error", null)
                        .putString("last_auto_backup_file", result.fileInfo.fileName)
                        .putString("last_auto_backup_summary", result.fileInfo.recordSummary)
                        .apply()

                    Result.success()
                }
                is BackupResult.Error -> {
                    prefs.edit()
                        .putString("last_auto_backup_status", "FAILED")
                        .putString("last_auto_backup_error", result.message)
                        .apply()

                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }
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
    }
}
