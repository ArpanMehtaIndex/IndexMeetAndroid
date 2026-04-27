package com.index.demoindexmettingroomreminderapp.worker.sync

import android.content.Context
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.data.Emirate
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.utils.parseEventDateTime
import com.index.demoindexmettingroomreminderapp.web.repository.MeetingAppRepo
import com.index.demoindexmettingroomreminderapp.worker.countdown.CountdownScheduler
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class MeetingSyncOutcome(
    val success: Boolean,
    val shouldRetry: Boolean = false
)

object MeetingSyncEngine {
    suspend fun run(context: Context): MeetingSyncOutcome {
        val selectedEmirate = PreferenceHelper.getSelectedEmirate(context)
        if (selectedEmirate == null) {
            AppLog.d("MeetingSyncEngine", "Skipping sync; no emirate selected.")
            return MeetingSyncOutcome(success = true)
        }

        val repository = MeetingAppRepo(context)

        return try {
            ensureAccessToken(context, repository)

            val userEmail = when (selectedEmirate) {
                Emirate.ABU_DHABI -> "auh@index.ae"
                Emirate.DUBAI -> "dubai@index.ae"
                Emirate.SHARJAH -> "sharjah@index.ae"
                Emirate.RAS_AL_KHAIMAH -> "rak@index.ae"
                Emirate.DESIGN -> "design.meeting@index.ae"
            }

            val (startDateTime, endDateTime) = buildUtcWindow()
            AppLog.d(
                "MeetingSyncEngine",
                "Fetching meetings for $userEmail from $startDateTime to $endDateTime"
            )

            val result = repository.getCalendarEvents(
                userEmail,
                startDateTime,
                endDateTime,
                Constants.TOP_MEETING_COUNT
            ).first()

            result.fold(
                onSuccess = { response ->
                    val now = System.currentTimeMillis()
                    val activeMeeting = response.value.firstOrNull { event ->
                        val startTime =
                            parseEventDateTime(event.start.dateTime, event.start.timeZone)?.time
                        val endTime =
                            parseEventDateTime(event.end.dateTime, event.end.timeZone)?.time
                        startTime != null && endTime != null && now in startTime until endTime
                    }

                    CountdownScheduler.scheduleCountdownWorkers(context, response.value)

                    val statusText = activeMeeting?.subject?.let { subject ->
                        "$subject: ${Constants.ONGOING}"
                    } ?: Constants.NO_MEETING_IN_PROGRESS
                    MeetingStatusNotifier.show(context, statusText)

                    AppLog.d(
                        "MeetingSyncEngine",
                        "Sync complete. Active meeting: ${activeMeeting?.subject ?: "none"}"
                    )
                    MeetingSyncOutcome(success = true)
                },
                onFailure = { error ->
                    AppLog.e("MeetingSyncEngine", "Calendar fetch failed: ${error.message}")
                    MeetingSyncOutcome(success = false, shouldRetry = true)
                }
            )
        } catch (e: Exception) {
            AppLog.e("MeetingSyncEngine", "Sync failed: ${e.message}")
            MeetingSyncOutcome(success = false, shouldRetry = true)
        }
    }

    private suspend fun ensureAccessToken(context: Context, repository: MeetingAppRepo) {
        if (!PreferenceHelper.isTokenExpired(context)
            && PreferenceHelper.getAccessToken(context) != null
        ) {
            return
        }

        val tokenResult = repository.getAccessToken().first()
        tokenResult.fold(
            onSuccess = { tokenResponse ->
                PreferenceHelper.saveTokenResponse(context, tokenResponse)
            },
            onFailure = { error ->
                throw IllegalStateException("Token refresh failed: ${error.message}", error)
            }
        )
    }

    private fun buildUtcWindow(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val utcTimeZone = TimeZone.getTimeZone("UTC")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = utcTimeZone
        }

        calendar.set(Calendar.HOUR_OF_DAY, 7)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startDateTime = isoFormat.format(calendar.time)

        calendar.set(Calendar.HOUR_OF_DAY, 22)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val endDateTime = isoFormat.format(calendar.time)

        return startDateTime to endDateTime
    }
}
