package com.index.demoindexmettingroomreminderapp.background.worker.recovery

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.index.demoindexmettingroomreminderapp.background.worker.sync.MeetingSyncScheduler
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.utils.AppLog


class RecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        RecoveryScheduler.schedulePeriodicRecovery(applicationContext)

        if (PreferenceHelper.getSelectedEmirate(applicationContext) == null) {
            AppLog.d("RecoveryWorker", "Skipping recovery; no emirate selected.")
            return Result.success()
        }

        return try {
            MeetingSyncScheduler.scheduleImmediateSync(applicationContext)
            AppLog.d("RecoveryWorker", "Recovery sync worker requested.")
            Result.success()
        } catch (e: Exception) {
            AppLog.e("RecoveryWorker", "Failed to request recovery sync worker: ${e.message}")
            Result.retry()
        }
    }
}
