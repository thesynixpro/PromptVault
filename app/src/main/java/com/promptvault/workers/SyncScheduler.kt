package com.promptvault.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueueNow(wifiOnly: Boolean = false) {
        workManager.enqueueUniqueWork(
            WORK_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly))
                .build()
        )
    }

    fun enqueueSoon(wifiOnly: Boolean = false) {
        workManager.enqueueUniqueWork(
            WORK_QUEUED,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly))
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
        )
    }

    fun retryFailed(wifiOnly: Boolean = false) {
        enqueueNow(wifiOnly)
    }

    fun configurePeriodic(enabled: Boolean, wifiOnly: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_PERIODIC)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints(wifiOnly))
                .build()
        )
    }

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    companion object {
        const val WORK_IMMEDIATE = "promptvault_sync_now"
        const val WORK_QUEUED = "promptvault_sync_queued"
        const val WORK_PERIODIC = "promptvault_sync_periodic"
    }
}
