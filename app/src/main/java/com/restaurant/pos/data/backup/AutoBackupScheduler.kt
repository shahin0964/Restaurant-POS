package com.restaurant.pos.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {

    const val FREQUENCY_DAILY = "Daily"
    const val FREQUENCY_WEEKLY = "Weekly"

    fun scheduleOrUpdate(context: Context, enabled: Boolean, frequency: String = FREQUENCY_DAILY) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            workManager.cancelUniqueWork(AutoBackupWorker.UNIQUE_WORK_NAME)
            return
        }

        val repeatIntervalHours = if (frequency == FREQUENCY_WEEKLY) 7 * 24L else 24L

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            repeatIntervalHours, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.UNIQUE_WORK_NAME)
    }
}
