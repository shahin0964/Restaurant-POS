package com.restaurant.pos.data.backupv2

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoBackupSchedulerV2 {

    const val WORK_NAME = "pos_auto_backup_work_v2"
    private const val PREFS_NAME = "auto_backup_settings"

    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_INTERVAL_HOURS = "auto_backup_interval_hours"
    private const val KEY_LAST_TIMESTAMP = "last_auto_backup_timestamp"
    private const val KEY_LAST_STATUS = "last_auto_backup_status"
    private const val KEY_LAST_MESSAGE = "last_auto_backup_message"
    private const val KEY_LAST_FILE_NAME = "last_auto_backup_filename"

    fun isAutoBackupEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun getAutoBackupIntervalHours(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_INTERVAL_HOURS, 24)
    }

    fun getLastBackupTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_TIMESTAMP, 0L)
    }

    fun getLastBackupStatus(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_STATUS, "NEVER") ?: "NEVER"
    }

    fun getLastBackupMessage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_MESSAGE, "") ?: ""
    }

    fun getLastBackupFilename(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_FILE_NAME, "") ?: ""
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean, intervalHours: Int = 24) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_INTERVAL_HOURS, intervalHours)
            .apply()

        if (enabled) {
            scheduleWork(context, intervalHours, ExistingPeriodicWorkPolicy.UPDATE)
        } else {
            cancelWork(context)
        }
    }

    fun initializeOnAppStart(context: Context) {
        if (isAutoBackupEnabled(context)) {
            val intervalHours = getAutoBackupIntervalHours(context)
            scheduleWork(context, intervalHours, ExistingPeriodicWorkPolicy.KEEP)
        }
    }

    fun scheduleWork(
        context: Context,
        intervalHours: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
    ) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorkerV2>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                policy,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateLastBackupResult(
        context: Context,
        timestamp: Long,
        status: String,
        message: String,
        filename: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_TIMESTAMP, timestamp)
            .putString(KEY_LAST_STATUS, status)
            .putString(KEY_LAST_MESSAGE, message)
            .putString(KEY_LAST_FILE_NAME, filename)
            .apply()
    }
}
