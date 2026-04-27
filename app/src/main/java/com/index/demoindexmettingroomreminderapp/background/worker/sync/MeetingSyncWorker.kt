package com.index.demoindexmettingroomreminderapp.background.worker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper

class MeetingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val outcome = MeetingSyncEngine.run(applicationContext)

        if (PreferenceHelper.getSelectedEmirate(applicationContext) != null) {
            MeetingSyncScheduler.scheduleNextSync(applicationContext)
        }

        return when {
            outcome.success -> Result.success()
            outcome.shouldRetry -> Result.retry()
            else -> Result.failure()
        }
    }
}
