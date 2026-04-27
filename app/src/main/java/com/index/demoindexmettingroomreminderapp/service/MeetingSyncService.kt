package com.index.demoindexmettingroomreminderapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.index.demoindexmettingroomreminderapp.R
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.worker.sync.MeetingSyncEngine
import com.index.demoindexmettingroomreminderapp.worker.sync.MeetingSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeetingSyncService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    companion object {
        private const val ACTION_START =
            "com.index.demoindexmettingroomreminderapp.action.MEETING_SYNC_START"
        private const val ACTION_STOP =
            "com.index.demoindexmettingroomreminderapp.action.MEETING_SYNC_STOP"
        private const val CHANNEL_ID = "MEETING_SYNC_CHANNEL"

        /**
         * Starts a one-shot foreground sync when the app is already in the foreground.
         */
        fun start(context: Context) {
            val intent = Intent(context, MeetingSyncService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeetingSyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            else -> startSyncOnce()
        }
        return START_NOT_STICKY
    }

    private fun startSyncOnce() {
        if (syncJob?.isActive == true) return

        startForeground(
            Constants.MEETING_STATUS_NOTIFICATION_ID,
            createNotification(Constants.FETCHING_MEETING_SCHEDULE)
        )

        syncJob = scope.launch {
            try {
                val outcome = MeetingSyncEngine.run(applicationContext)
                if (!outcome.success) {
                    AppLog.e("MeetingSyncService", "Foreground sync finished with retryable failure.")
                }
                MeetingSyncScheduler.scheduleNextSync(applicationContext)
            } catch (e: Exception) {
                AppLog.e("MeetingSyncService", "Foreground sync failed: ${e.message}")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            Constants.MEETING_STATUS,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = Constants.MEETING_COUNT_DOWN_DETAILS
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
