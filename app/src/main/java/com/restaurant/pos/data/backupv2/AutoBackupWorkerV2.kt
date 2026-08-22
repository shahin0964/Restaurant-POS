package com.restaurant.pos.data.backupv2

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.restaurant.pos.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoBackupWorkerV2(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        // Ensure auto backup is enabled
        if (!AutoBackupSchedulerV2.isAutoBackupEnabled(context)) {
            return@withContext Result.success()
        }

        val database = AppDatabase.getInstance(context)

        // Validate active Firebase session prior to backup operations
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            android.util.Log.w("AutoBackupWorkerV2", "AutoBackup aborted: No authenticated Firebase session.")
            return@withContext Result.failure()
        }

        val backupEngine = BackupEngineV2(context, database)

        val backupDir = File(context.filesDir, "auto_backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val timestampStr = sdf.format(Date())
        val filename = "pos_backup_v2_auto_${timestampStr}.json"
        val backupFile = File(backupDir, filename)

        try {
            // 1. Export backup using BackupEngineV2
            val exportResult = backupFile.outputStream().use { stream ->
                backupEngine.exportBackup(stream)
            }

            if (exportResult !is BackupExportResultV2.Success) {
                val errorMsg = (exportResult as? BackupExportResultV2.Error)?.message ?: "Export failure"
                if (backupFile.exists()) backupFile.delete()
                AutoBackupSchedulerV2.updateLastBackupResult(
                    context, System.currentTimeMillis(), "FAILED", errorMsg, ""
                )
                return@withContext Result.retry()
            }

            // 2. Integrity Verification: Verify file exists and is parseable
            if (!backupFile.exists() || backupFile.length() == 0L) {
                if (backupFile.exists()) backupFile.delete()
                AutoBackupSchedulerV2.updateLastBackupResult(
                    context, System.currentTimeMillis(), "FAILED", "Backup file was empty or missing.", ""
                )
                return@withContext Result.retry()
            }

            val fileUri = Uri.fromFile(backupFile)
            val validationResult = backupEngine.importAndValidateFromUri(fileUri)

            if (validationResult !is BackupValidationResultV2.Valid) {
                val reason = (validationResult as? BackupValidationResultV2.Invalid)?.reason ?: "Integrity check failed"
                if (backupFile.exists()) backupFile.delete()
                AutoBackupSchedulerV2.updateLastBackupResult(
                    context, System.currentTimeMillis(), "FAILED", "Validation failed: $reason", ""
                )
                return@withContext Result.retry()
            }

            // 3. Success: Save last result
            AutoBackupSchedulerV2.updateLastBackupResult(
                context, System.currentTimeMillis(), "SUCCESS", "Automatic backup completed successfully.", filename
            )

            // 4. Retention Policy: Keep latest 7 automatic backups
            cleanupOldAutoBackups(backupDir, keepCount = 7)

            return@withContext Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            if (backupFile.exists()) backupFile.delete()
            AutoBackupSchedulerV2.updateLastBackupResult(
                context, System.currentTimeMillis(), "FAILED", "Error: ${e.message}", ""
            )
            return@withContext Result.retry()
        }
    }

    private fun cleanupOldAutoBackups(backupDir: File, keepCount: Int = 7) {
        try {
            val autoBackupFiles = backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("pos_backup_v2_auto_") && file.name.endsWith(".json")
            } ?: return

            if (autoBackupFiles.size > keepCount) {
                val sortedFiles = autoBackupFiles.sortedByDescending { it.lastModified() }
                for (i in keepCount until sortedFiles.size) {
                    sortedFiles[i].delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
