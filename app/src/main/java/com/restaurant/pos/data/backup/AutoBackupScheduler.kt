package com.restaurant.pos.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
            workManager.cancelUniqueWork(AutoBackupWorker.UNIQUE_ONE_TIME_WORK_NAME)
            return
        }

        val repeatIntervalHours = if (frequency == FREQUENCY_WEEKLY) 7 * 24L else 24L

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 1. Existing periodic schedule (Daily / Weekly)
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

        // 2. Immediate one-time backup trigger on enable (Internet Billing Manager architecture)
        val immediateWorkRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            AutoBackupWorker.UNIQUE_ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            immediateWorkRequest
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(AutoBackupWorker.UNIQUE_WORK_NAME)
        workManager.cancelUniqueWork(AutoBackupWorker.UNIQUE_ONE_TIME_WORK_NAME)
    }
}
