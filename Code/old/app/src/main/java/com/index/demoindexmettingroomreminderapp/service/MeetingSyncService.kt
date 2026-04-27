package com.index.demoindexmettingroomreminderapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.index.demoindexmettingroomreminderapp.R
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.data.Emirate
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.utils.parseEventDateTime
import com.index.demoindexmettingroomreminderapp.web.repository.MeetingAppRepo
import com.index.demoindexmettingroomreminderapp.worker.countdown.CountdownWorker
import com.index.demoindexmettingroomreminderapp.worker.countdown.CountdownScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MeetingSyncService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private val repository by lazy { MeetingAppRepo(applicationContext) }
    private var shouldRestart = true
    private var activeMeetingEndTimeMillis: Long = 0L

    companion object {
        private const val ACTION_START = "com.index.demoindexmettingroomreminderapp.action.MEETING_SYNC_START"
        private const val ACTION_STOP = "com.index.demoindexmettingroomreminderapp.action.MEETING_SYNC_STOP"
        private const val EXTRA_POLL_INTERVAL_MS = "EXTRA_POLL_INTERVAL_MS"

        private const val CHANNEL_ID = "MEETING_SYNC_CHANNEL"
        private const val NOTIFICATION_ID = 54321
        private const val DEFAULT_POLL_INTERVAL_MS = 60_000L

        /**
         * Class: MeetingSyncService.kt
         * Method: start
         * Created By: Arpan Mehta
         * Created On: 05/02/2026
         * Modified On: 05/02/2026
         * Param: [context: Context, pollIntervalMs: Long]
         * Description: Starts the foreground sync service with an optional polling interval.
         **/
        fun start(context: Context, pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS) {
            val intent = Intent(context, MeetingSyncService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_POLL_INTERVAL_MS, pollIntervalMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Class: MeetingSyncService.kt
         * Method: stop
         * Created By: Arpan Mehta
         * Created On: 05/02/2026
         * Modified On: 05/02/2026
         * Param: [context: Context]
         * Description: Stops the foreground sync service.
         **/
        fun stop(context: Context) {
            val intent = Intent(context, MeetingSyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: onStartCommand
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [intent: Intent?, flags: Int, startId: Int]
     * Description: Handles start/stop commands and kicks off the sync loop.
     **/
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shouldRestart = false
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val interval = intent?.getLongExtra(EXTRA_POLL_INTERVAL_MS, DEFAULT_POLL_INTERVAL_MS)
                    ?: DEFAULT_POLL_INTERVAL_MS
                startSyncLoop(interval)
            }
        }
        return START_STICKY
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: startSyncLoop
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [pollIntervalMs: Long]
     * Description: Runs the periodic calendar sync loop and schedules workers.
     *              1) Find the meeting that is happening right now.
     *              2) Store when the active meeting ends so we can pause polling until then.
     *              3) Schedule the countdown worker for the active meeting (if any).
     **/
    private fun startSyncLoop(pollIntervalMs: Long) {
        if (syncJob?.isActive == true) return

        startForeground(NOTIFICATION_ID, createNotification(Constants.NO_MEETING_IN_PROGRESS))

        syncJob = scope.launch {
            while (isActive) {
                AppLog.d("MeetingSyncService", "Sync tick: polling calendar.")
                try {
                    val now = System.currentTimeMillis()
                    if (activeMeetingEndTimeMillis > now) {
                        AppLog.d(
                            "MeetingSyncService",
                            "Skipping sync; meeting ongoing until $activeMeetingEndTimeMillis"
                        )
                        delay(pollIntervalMs)
                        continue
                    }
                    val selectedEmirate = PreferenceHelper.getSelectedEmirate(applicationContext)
                    if (selectedEmirate == null) {
                        AppLog.d("MeetingSyncService", "No emirate selected; waiting to sync.")
                        delay(30_000)
                        continue
                    }

                    ensureAccessToken()

                    val userEmail = when (selectedEmirate) {
                        Emirate.ABU_DHABI -> "auh@index.ae"
                        Emirate.DUBAI -> "dubai@index.ae"
                        Emirate.SHARJAH -> "sharjah@index.ae"
                        Emirate.RAS_AL_KHAIMAH -> "rak@index.ae"
                        Emirate.DESIGN -> "design.meeting@index.ae"
                    }

                    val (startDateTime, endDateTime) = buildUtcWindow()
                    AppLog.d(
                        "MeetingSyncService",
                        "Calling calendar API for $userEmail from $startDateTime to $endDateTime"
                    )
                    val result = repository.getCalendarEvents(userEmail, startDateTime, endDateTime, Constants.TOP_MEETING_COUNT).first()
                    result.fold(
                        onSuccess = { response ->
                            AppLog.d(
                                "MeetingSyncService",
                                "Calendar sync success: ${response.value.size} events"
                            )
                            // Find the meeting that is happening right now.
                            val activeMeeting = response.value.firstOrNull { event ->
                                val startTime = parseEventDateTime(event.start.dateTime, event.start.timeZone)?.time
                                val endTime = parseEventDateTime(event.end.dateTime, event.end.timeZone)?.time
                                startTime != null && endTime != null && now in startTime until endTime
                            }
                            // Store when the active meeting ends so we can pause polling until then.
                            activeMeetingEndTimeMillis = activeMeeting?.let { event ->
                                parseEventDateTime(event.end.dateTime, event.end.timeZone)?.time ?: 0L
                            } ?: 0L
                            // Schedule the countdown worker for the active meeting (if any).
                            CountdownScheduler.scheduleCountdownWorkers(applicationContext, response.value)
                        },
                        onFailure = { e ->
                            AppLog.e("MeetingSyncService", "Calendar fetch failed: ${e.message}")
                        }
                    )
                } catch (e: Exception) {
                    AppLog.e("MeetingSyncService", "Sync error: ${e.message}")
                }

                delay(pollIntervalMs)
            }
        }
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: ensureAccessToken
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Refreshes the access token if missing or expired.
     **/
    private suspend fun ensureAccessToken() {
        if (!PreferenceHelper.isTokenExpired(applicationContext)
            && PreferenceHelper.getAccessToken(applicationContext) != null
        ) {
            return
        }

        val tokenResult = repository.getAccessToken().first()
        tokenResult.fold(
            onSuccess = { tokenResponse ->
                PreferenceHelper.saveTokenResponse(applicationContext, tokenResponse)
            },
            onFailure = { e ->
                AppLog.e("MeetingSyncService", "Token refresh failed: ${e.message}")
            }
        )
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: buildUtcWindow
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Builds the UTC time window used for calendar queries.
     **/
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

    /**
     * Class: MeetingSyncService.kt
     * Method: createNotification
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [contentText: String]
     * Description: Creates the foreground notification for the sync service.
     **/
    private fun createNotification(contentText: String): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_room)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: createNotificationChannel
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Creates the notification channel used by the sync service.
     **/
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Constants.MEETING_COUNT_DOWN,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Constants.MEETING_COUNT_DOWN_DETAILS
            }
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: scheduleServiceRestart
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Schedules a restart of the sync service after a short delay.
     **/
    private fun scheduleServiceRestart() {
        val restartIntent = Intent(applicationContext, MeetingSyncService::class.java).apply {
            action = ACTION_START
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            1001,
            restartIntent,
            flags
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + /*60_000L*/Constants.TEN_MINUTES_IN_MILLIS
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: onTaskRemoved
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [rootIntent: Intent?]
     * Description: Restarts the service if it was removed by the system or user.
     **/
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (shouldRestart) {
            scheduleServiceRestart()
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: onDestroy
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Cancels the sync job and cleans up the service.
     **/
    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        if (shouldRestart) {
            scheduleServiceRestart()
        }
    }

    /**
     * Class: MeetingSyncService.kt
     * Method: onBind
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [intent: Intent?]
     * Description: Returns null because this is a started service, not a bound service.
     **/
    override fun onBind(intent: Intent?): IBinder? = null
}
