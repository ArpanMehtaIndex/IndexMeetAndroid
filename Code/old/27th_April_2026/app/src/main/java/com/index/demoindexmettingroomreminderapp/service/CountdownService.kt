package com.index.demoindexmettingroomreminderapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.index.demoindexmettingroomreminderapp.R
import com.index.demoindexmettingroomreminderapp.data.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

class CountdownService : Service(), TextToSpeech.OnInitListener {

    private var countdownJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "MEETING_COUNTDOWN_CHANNEL"
        const val NOTIFICATION_ID = 12345
        const val KEY_MEETING_SUBJECT = "KEY_MEETING_SUBJECT"
        const val KEY_MEETING_START_TIME = "KEY_MEETING_START_TIME"
        const val KEY_MEETING_END_TIME = "KEY_END_TIME_MILLIS"
    }

    /**
     * Class: CountdownService.kt
     * Method: onCreate
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Initializes the service and TTS engine.
     **/
    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    /**
     * Class: CountdownService.kt
     * Method: onStartCommand
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [intent: Intent?, flags: Int, startId: Int]
     * Description: Starts the countdown loop and foreground notification updates.
     **/
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Cancel any existing countdown job before starting a new one.
        countdownJob?.cancel()

        val meetingSubject = intent?.getStringExtra(KEY_MEETING_SUBJECT) ?: "Current Meeting"
        val startTimeMillis = intent?.getLongExtra(KEY_MEETING_START_TIME, 0L) ?: 0L
        val endTimeMillis = intent?.getLongExtra(KEY_MEETING_END_TIME, 0L) ?: 0L

        if (endTimeMillis == 0L || System.currentTimeMillis() >= endTimeMillis) {
            stopSelf()
            return START_NOT_STICKY
        }

        countdownJob = scope.launch {
            val countdownStartTime = endTimeMillis - Constants.TEN_MINUTES_IN_MILLIS

            // Loop until the meeting is over.
            while (System.currentTimeMillis() < endTimeMillis) {
                val now = System.currentTimeMillis()
                val notificationText = when {
                    // State 0: Meeting hasn't started yet.
                    now < startTimeMillis -> Constants.PREPARING_MEETING_STATUS
                    // State 1: Countdown is active (within the last 10 minutes)
                    now >= countdownStartTime -> {
                        val remainingSeconds = (endTimeMillis - now) / 1000
                        val minutes = remainingSeconds / 60
                        val seconds = remainingSeconds % 60
                        Log.d("CountdownService", "Minutes: $minutes, Seconds: $seconds")
                        String.format(Locale.getDefault(), "Ends in: %02d:%02d", minutes, seconds)
                    }
                    // State 2: Meeting is ongoing but not in the countdown window yet.
                    else -> Constants.ONGOING_MEETING
                }

                val notification = createNotification("$meetingSubject: $notificationText")
                startForeground(NOTIFICATION_ID, notification)

                delay(1000)
            }

            // --- At this point, the meeting has finished ---
            updateNotification("$meetingSubject: Meeting has ended.")
            val messageToSpeak = Constants.MEETING_FINISHED_MESSAGE
            speakMessage(messageToSpeak)
            delay(5000) // Wait for TTS
            stopSelf()
        }

        return START_STICKY
    }

    /**
     * Class: CountdownService.kt
     * Method: createNotification
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [contentText: String]
     * Description: Creates the ongoing countdown notification.
     **/
    private fun createNotification(contentText: String): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Meeting Status")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_room)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Prevent sound/vibration on every update
            .setSilent(true)
            .setShowWhen(false) // Don't show timestamp to avoid confusion
            .setWhen(System.currentTimeMillis()) // Force update by setting current time
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensure it stays visible
            .setAutoCancel(false) // Keep notification visible
            .build()
    }

    /**
     * Class: CountdownService.kt
     * Method: updateNotification
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [contentText: String]
     * Description: Updates the countdown notification content.
     **/
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(contentText)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Class: CountdownService.kt
     * Method: createNotificationChannel
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Creates the notification channel for countdown updates.
     **/
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Meeting Countdown Channel"
            val descriptionText = "Notifications for meeting end times"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Class: CountdownService.kt
     * Method: onInit
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [status: Int]
     * Description: Handles TTS initialization and language setup.
     **/
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!")
            } else {
                isTtsInitialized = true
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    /**
     * Class: CountdownService.kt
     * Method: speakMessage
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [message: String]
     * Description: Speaks a message using the initialized TTS engine.
     **/
    private fun speakMessage(message: String) {
        if (isTtsInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    /**
     * Class: CountdownService.kt
     * Method: onDestroy
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Cancels countdown job and releases TTS resources.
     **/
    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    /**
     * Class: CountdownService.kt
     * Method: onBind
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [intent: Intent?]
     * Description: Returns null because this is a started service, not a bound service.
     **/
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
