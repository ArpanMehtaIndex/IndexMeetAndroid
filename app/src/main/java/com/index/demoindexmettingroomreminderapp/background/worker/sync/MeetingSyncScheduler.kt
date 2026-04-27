package com.index.demoindexmettingroomreminderapp.background.worker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.index.demoindexmettingroomreminderapp.data.Constants
import java.util.concurrent.TimeUnit

object MeetingSyncScheduler {
    fun scheduleImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<MeetingSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(syncConstraints())
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            Constants.MEETING_SYNC_WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleNextSync(context: Context, delayMillis: Long = Constants.TEN_MINUTES_IN_MILLIS) {
        val request = OneTimeWorkRequestBuilder<MeetingSyncWorker>()
            .setConstraints(syncConstraints())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            Constants.MEETING_SYNC_WORKER_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun syncConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
