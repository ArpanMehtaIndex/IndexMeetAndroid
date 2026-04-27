package com.index.demoindexmettingroomreminderapp.worker.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.index.demoindexmettingroomreminderapp.R
import com.index.demoindexmettingroomreminderapp.data.Constants

object MeetingStatusNotifier {
    fun show(context: Context, contentText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val notification = NotificationCompat.Builder(context, Constants.MEETING_STATUS_CHANNEL_ID)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_room)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

        notificationManager.notify(Constants.MEETING_STATUS_NOTIFICATION_ID, notification)
    }

    private fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            Constants.MEETING_STATUS_CHANNEL_ID,
            Constants.MEETING_STATUS,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = Constants.MEETING_COUNT_DOWN_DETAILS
        }
        notificationManager.createNotificationChannel(channel)
    }
}
