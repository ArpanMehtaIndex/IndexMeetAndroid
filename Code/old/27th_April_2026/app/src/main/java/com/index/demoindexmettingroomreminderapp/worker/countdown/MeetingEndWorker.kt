package com.index.demoindexmettingroomreminderapp.worker.countdown

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.index.demoindexmettingroomreminderapp.service.CountdownService

class MeetingEndWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_MEETING_ID = "KEY_MEETING_ID"
        const val KEY_MEETING_START_TIME = "KEY_MEETING_START_TIME"
        const val KEY_MEETING_END_TIME = "KEY_MEETING_END_TIME"
        const val KEY_MEETING_SUBJECT = "KEY_MEETING_SUBJECT"
    }

    override suspend fun doWork(): Result {
        val meetingSubject = inputData.getString(KEY_MEETING_SUBJECT) ?: "Current Meeting"
        val startTimeMillis = inputData.getLong(KEY_MEETING_START_TIME, 0L)
        val endTimeMillis = inputData.getLong(KEY_MEETING_END_TIME, 0L)

        if (endTimeMillis == 0L) {
            return Result.failure()
        }

        val intent = Intent(appContext, CountdownService::class.java).apply {
            putExtra(CountdownService.KEY_MEETING_SUBJECT, meetingSubject)
            putExtra(CountdownService.KEY_MEETING_START_TIME, startTimeMillis)
            putExtra(CountdownService.KEY_MEETING_END_TIME, endTimeMillis)
        }

        ContextCompat.startForegroundService(appContext, intent)

        return Result.success()
    }
}