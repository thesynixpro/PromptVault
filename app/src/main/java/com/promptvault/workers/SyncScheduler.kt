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
        cancelLegacyWork()
        workManager.enqueueUniqueWork(
            WORK_SYNC,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly))
                .build()
        )
    }

    fun enqueueSoon(wifiOnly: Boolean = false) {
        cancelLegacyWork()
        workManager.enqueueUniqueWork(
            WORK_SYNC,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(wifiOnly))
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
        )
    }

    fun retryFailed(wifiOnly: Boolean = false) = enqueueNow(wifiOnly)

    fun configurePeriodic(enabled: Boolean, wifiOnly: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_PERIODIC)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints(wifiOnly))
                .build()
        )
    }

    /**
     * Applies settings changes to scheduled work. One-time work is deliberately left intact so an
     * explicit cloud delete is not lost when automatic backup is switched off.
     */
    fun applySettings(autoBackup: Boolean, wifiOnly: Boolean) {
        configurePeriodic(autoBackup, wifiOnly)
        if (autoBackup) enqueueSoon(wifiOnly)
    }

    private fun cancelLegacyWork() {
        workManager.cancelUniqueWork(WORK_IMMEDIATE)
        workManager.cancelUniqueWork(WORK_QUEUED)
    }

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    companion object {
        const val WORK_SYNC = "promptvault_sync"
        const val WORK_IMMEDIATE = "promptvault_sync_now"
        const val WORK_QUEUED = "promptvault_sync_queued"
        const val WORK_PERIODIC = "promptvault_sync_periodic"
    }
}
