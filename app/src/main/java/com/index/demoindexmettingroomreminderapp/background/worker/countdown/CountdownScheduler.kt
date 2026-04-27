package com.index.demoindexmettingroomreminderapp.background.worker.countdown

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.utils.parseEventDateTime
import com.index.demoindexmettingroomreminderapp.web.model.response.CalendarEvent
import java.util.concurrent.TimeUnit

object CountdownScheduler {
    /**
     * Class: CountdownScheduler.kt
     * Method: scheduleCountdownWorkers
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [context: Context, meetings: List<CalendarEvent>]
     * Description: Cancels old countdown workers and schedules the active meeting worker.
     **/
    fun scheduleCountdownWorkers(context: Context, meetings: List<CalendarEvent>) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val now = System.currentTimeMillis()

        meetings.forEach { event ->
            workManager.cancelUniqueWork("${Constants.COUNTDOWN_TOKEN_WORKER_NAME}${event.id}")
            workManager.cancelUniqueWork("${Constants.LEGACY_MEETING_WORKER_NAME}${event.id}")
        }

        val activeMeeting = meetings.firstOrNull { event ->
            val startTime = parseEventDateTime(event.start.dateTime, event.start.timeZone)?.time
            val endTime = parseEventDateTime(event.end.dateTime, event.end.timeZone)?.time
            startTime != null && endTime != null && now in startTime until endTime
        } ?: return

        val startTime = parseEventDateTime(activeMeeting.start.dateTime, activeMeeting.start.timeZone)?.time ?: return
        val endTime = parseEventDateTime(activeMeeting.end.dateTime, activeMeeting.end.timeZone)?.time ?: return

        if (endTime <= now) {
            return
        }

        val inputData = Data.Builder()
            .putString(_root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.countdown.CountdownWorker.KEY_MEETING_SUBJECT, activeMeeting.subject)
            .putLong(_root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.countdown.CountdownWorker.KEY_MEETING_START_TIME, startTime)
            .putLong(_root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.countdown.CountdownWorker.KEY_MEETING_END_TIME, endTime)
            .build()

        val initialDelayMillis = (startTime - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<com.index.demoindexmettingroomreminderapp.background.worker.countdown.CountdownWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        // The unique name ensures we don't try to schedule the same meeting twice.
        val uniqueWorkName = "${Constants.COUNTDOWN_TOKEN_WORKER_NAME}${activeMeeting.id}"
        val existing = runCatching {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
        }.getOrNull()
        val hasRunning = existing?.any {
            it.state == androidx.work.WorkInfo.State.RUNNING ||
                it.state == androidx.work.WorkInfo.State.ENQUEUED
        } == true
        if (hasRunning) {
            AppLog.d("CountdownScheduler", "CountdownWorker already running for -> ${activeMeeting.subject}")
            return
        }

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE, // Replace if meeting details change
            request
        )
        AppLog.d("CountdownScheduler", "Enqueued CountdownWorker for -> ${activeMeeting.subject}")
    }
}
