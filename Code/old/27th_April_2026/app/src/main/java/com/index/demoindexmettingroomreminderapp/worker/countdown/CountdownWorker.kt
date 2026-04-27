package com.index.demoindexmettingroomreminderapp.worker.countdown

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.index.demoindexmettingroomreminderapp.BuildConfig
import com.index.demoindexmettingroomreminderapp.R
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.utils.OverlayPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale


// --- Create a Custom Lifecycle Owner class inside or outside your worker ---
private class FloatingViewLifecycleOwner : LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /**
     * Class: CountdownWorker.kt
     * Method: onCreate
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Initializes the custom lifecycle owner state for the floating view.
     **/
    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * Class: CountdownWorker.kt
     * Method: onResume
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Moves the custom lifecycle owner to resumed state.
     **/
    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Class: CountdownWorker.kt
     * Method: onStop
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Moves the custom lifecycle owner to stopped state.
     **/
    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /**
     * Class: CountdownWorker.kt
     * Method: onDestroy
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Cleans up the custom lifecycle owner state.
     **/
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
class CountdownWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var countdownOverlayView: ComposeView? = null
    private var finishedOverlayView: ComposeView? = null
    private var countdownText by mutableStateOf("")
    private var lifecycleOwner: FloatingViewLifecycleOwner? = null
    private var beepPlayer: MediaPlayer? = null
    private var meetingSBJ: String = ""
    @Volatile
    private var overlaysEnabled = true
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "MEETING_COUNTDOWN_CHANNEL"
        const val NOTIFICATION_ID = 12345
        const val KEY_MEETING_SUBJECT = "KEY_MEETING_SUBJECT"
        const val KEY_MEETING_START_TIME = "KEY_MEETING_START_TIME"
        const val KEY_MEETING_END_TIME = "KEY_MEETING_END_TIME"
        const val ALERTING_NOTIFICATION_CHANNEL_ID = "MEETING_ALERT_CHANNEL"
        private val overlayViews = mutableSetOf<View>()
    }

    private fun addOverlayView(view: View) {
        synchronized(overlayViews) {
            overlayViews.add(view)
        }
    }

    private fun removeOverlayView(view: View) {
        synchronized(overlayViews) {
            overlayViews.remove(view)
        }
    }

    private fun clearAllOverlayViews() {
        val viewsToRemove = synchronized(overlayViews) {
            overlayViews.toList().also { overlayViews.clear() }
        }
        viewsToRemove.forEach { attachedView ->
            try {
                if (attachedView.isAttachedToWindow) {
                    try {
                        windowManager.updateViewLayout(
                            attachedView,
                            attachedView.layoutParams as WindowManager.LayoutParams
                        )
                    } catch (_: Exception) {
                        // Ignore update errors.
                    }
                    attachedView.visibility = View.GONE
                    windowManager.removeViewImmediate(attachedView)
                }
            } catch (_: Exception) {
                // Ignore cleanup errors.
            }
        }
        hardSweepOverlayRemoval()
    }

    /*
     * forceWindowManagerRefresh() was an earlier workaround to force a relayout by
     * briefly attaching and removing a dummy view. It is now replaced by the
     * hardSweepOverlayRemoval() cleanup below.
     */
    /* private fun forceWindowManagerRefresh() {
         try {
             val dummyView = View(context)
             val params = WindowManager.LayoutParams(
                 1,
                 1,
                 WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                 WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                 PixelFormat.TRANSLUCENT
             ).apply {
                 gravity = Gravity.TOP or Gravity.START
                 x = 0
                 y = 0
             }
             windowManager.addView(dummyView, params)
             windowManager.removeViewImmediate(dummyView)
         } catch (_: Exception) {
             // Ignore refresh errors.
         }
     }*/

    private fun hardSweepOverlayRemoval() {
        val viewsToRemove = synchronized(overlayViews) {
            overlayViews.toList()
        }
        viewsToRemove.forEach { view ->
            try {
                if (view.isAttachedToWindow) {
                    view.visibility = View.GONE
                    windowManager.removeViewImmediate(view)
                }
            } catch (_: Exception) {
                // Ignore cleanup errors.
            }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val delayedViews = synchronized(overlayViews) {
                overlayViews.toList().also { overlayViews.clear() }
            }
            delayedViews.forEach { view ->
                try {
                    if (view.isAttachedToWindow) {
                        view.visibility = View.GONE
                        windowManager.removeViewImmediate(view)
                    }
                } catch (_: Exception) {
                    // Ignore cleanup errors.
                }
            }
        }, 250)
    }

    private fun canRenderOverlay(): Boolean {
        return overlaysEnabled && !isStopped && lifecycleOwner != null
    }

    /**
     * Class: CountdownWorker.kt
     * Method: doWork
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Runs the meeting countdown workflow and manages overlay/notifications.
     **/
    override suspend fun doWork(): Result {
        val meetingSubject = inputData.getString(KEY_MEETING_SUBJECT) ?: "Current Meeting"
        val startTimeMillis = inputData.getLong(KEY_MEETING_START_TIME, 0L)
        val endTimeMillis = inputData.getLong(KEY_MEETING_END_TIME, 0L)

        this.meetingSBJ = meetingSubject
        overlaysEnabled = true
        // Defensive cleanup in case a previous worker left an overlay behind.
        clearAllOverlayViews()
        hideAndRemoveCountdownOverlayView()
        hideAndRemoveFinishedOverlayView()

        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            AppLog.e(
                "CountdownWorker",
                "SYSTEM_ALERT_WINDOW permission not granted."
            )
            hideAndRemoveCountdownOverlayView()
            hideAndRemoveFinishedOverlayView()
            return Result.failure()
        }

        setForeground(createForegroundInfo(Constants.FETCHING_MEETING_SCHEDULE))

        if (endTimeMillis == 0L || System.currentTimeMillis() >= endTimeMillis) {
            overlaysEnabled = false
            updateNotification(Constants.NO_MEETING_IN_PROGRESS, false)
            hideAndRemoveCountdownOverlayView()
            hideAndRemoveFinishedOverlayView()
            AppLog.e(
                "CountdownWorker",
                "NO meeting 125"
            )
            return Result.success()
        }

        AppLog.d("CountdownWorker:", " Starting for '$meetingSubject'")

        // Initialize TTS
        tts = TextToSpeech(context, this)
        delay(500) // Give TTS a moment to initialize

        try {
            withContext(Dispatchers.Main) {
                // Create the custom lifecycle and the view
                lifecycleOwner = FloatingViewLifecycleOwner()
                lifecycleOwner?.onCreate()
                countdownText = ""
                createCountdownOverlayView()
                lifecycleOwner?.onResume()
            }

            val nowAtStart = System.currentTimeMillis()
            val initialStatus = when {
                nowAtStart < startTimeMillis -> {
                    AppLog.d(
                        "CountdownWorker",
                        "Initial status: PRE_START (now < start) => NO_MEETING_IN_PROGRESS 151"
                    )
                    Constants.NO_MEETING_IN_PROGRESS
                }
                nowAtStart in startTimeMillis until endTimeMillis -> {
                    AppLog.d(
                        "CountdownWorker",
                        "Initial status: IN_PROGRESS (start <= now < end) => Ongoing 158"
                    )
                    "$meetingSubject: ${Constants.ONGOING}"
                }
                else -> {
                    AppLog.d(
                        "CountdownWorker",
                        "Initial status: POST_END (now >= end) => NO_MEETING_IN_PROGRESS 165"
                    )
                    Constants.NO_MEETING_IN_PROGRESS
                }
            }
            val isInitialOngoing = initialStatus == "$meetingSubject: ${Constants.ONGOING}"
            val foregroundInfo = createForegroundInfo(initialStatus, isInitialOngoing)
            setForeground(foregroundInfo)

            while (!isStopped && System.currentTimeMillis() < endTimeMillis) {
                val now = System.currentTimeMillis()
                val countdownStartTime = endTimeMillis - Constants.TEN_MINUTES_IN_MILLIS

                // Check if we have just entered the 10-minute countdown window
                if (now >= countdownStartTime) {
                    if (now < startTimeMillis) {
                        withContext(Dispatchers.Main) {
                            countdownText = ""
                            countdownOverlayView?.visibility = android.view.View.GONE
                        }
                        // Wait until the actual meeting start time before re-evaluating.
                        val timeToWaitUntilStart = startTimeMillis - now
                        if (timeToWaitUntilStart > 0) {
                            delay(timeToWaitUntilStart)
                        }
                        continue
                    }

                    val initialRemainingMillis = endTimeMillis - System.currentTimeMillis()
                    val initialSeconds = (initialRemainingMillis / 1000).coerceAtLeast(0).toInt()
                    val initialMinutes = initialSeconds / 60
                    val initialSecs = initialSeconds % 60
                    val initialTimeString = "${initialMinutes.toString().padStart(2, '0')}:${
                        initialSecs.toString().padStart(2, '0')
                    }"
                    withContext(Dispatchers.Main) {
                        if (!canRenderOverlay()) return@withContext
                        countdownText =
                            "${Constants.MEETING_GOING_TO_END_IN} $initialTimeString ${Constants.MINUTES}"
                        hideAndRemoveCountdownOverlayView()
                        createCountdownOverlayView()
                        countdownOverlayView?.visibility = android.view.View.VISIBLE
                        countdownOverlayView?.invalidate()
                        countdownOverlayView?.requestLayout()
                    }
                    // --- We are in the countdown phase. Start showing notifications. ---
                    playBeep()

                    // Create the first, HIGH-PRIORITY notification to alert the user.
                    val firstAlertNotification = createNotification(
                        contentText = "${Constants.MEETING_GOING_TO_END_IN} 10 ${Constants.MINUTES}",
                        isAlerting = false // Use the high-priority channel
                    )
                    // Set the initial foreground state with this alerting notification.
                    setForeground(ForegroundInfo(NOTIFICATION_ID, firstAlertNotification))

                    // Now, start the second-by-second update loop.
                    var remainingMillis = endTimeMillis - System.currentTimeMillis()
                    while (!isStopped && remainingMillis > 0) {
                        val remainingSeconds = (remainingMillis / 1000).coerceAtLeast(0).toInt()
                        val minutes = remainingSeconds / 60
                        val seconds = remainingSeconds % 60
                        val timeString = "${minutes.toString().padStart(2, '0')}:${
                            seconds.toString().padStart(2, '0')
                        }"
                        // Update the notification SILENTLY using the low-priority channel
                        updateNotification(
                            "${Constants.MEETING_GOING_TO_END_IN} $timeString ${Constants.MINUTES}",
                            isAlerting = false
                        )
                        withContext(Dispatchers.Main) {
                            countdownText = "${Constants.MEETING_GOING_TO_END_IN} $timeString ${Constants.MINUTES}"
                        }
                        delay(1000)
                        remainingMillis = endTimeMillis - System.currentTimeMillis()
                    }
                    break
                } else {
                    withContext(Dispatchers.Main) {
                        countdownText = ""
                        countdownOverlayView?.visibility = android.view.View.GONE
                    }
                    // We are not yet in the countdown window. Just wait.
                    // Only show "Ongoing" once the meeting has actually started.
                    val statusText = if (now < startTimeMillis) {
                        AppLog.d("CountdownWorker", "status for $meetingSubject now=$now start=$startTimeMillis end=$endTimeMillis")
                        Constants.NO_MEETING_IN_PROGRESS

                    } else {
                        "$meetingSubject: ${Constants.ONGOING}"
                    }
                    val isOngoingStatus = statusText == "$meetingSubject: ${Constants.ONGOING}"
                    setForeground(createForegroundInfo(statusText, isOngoingStatus))
                    val timeToWait = countdownStartTime - now
                    if (timeToWait > 0) {
                        delay(timeToWait)
                    }
                }
            }

            // --- At this point, the meeting has finished ---
            withContext(Dispatchers.Main) {
                hideAndRemoveCountdownOverlayView()
            }
            updateNotification(Constants.MEETING_ENDED, false)
            withContext(Dispatchers.Main) {
                if (!isStopped) {
                    playBeep()
                }
                hideAndRemoveCountdownOverlayView()
                delay(2_000)
                if (canRenderOverlay()) {
                    showMeetingFinishedView()
                }
//                scheduleSingleSweep()
            }
            delay(60_000)
            withContext(Dispatchers.Main) {
                overlaysEnabled = false
                hideAndRemoveFinishedOverlayView()
            }
        } catch (e: Exception) {
            AppLog.e("CountdownWorker: Error during countdown", e.toString())
            return Result.failure()
        } finally {
            overlaysEnabled = false
            cleanupOverlayAndLifecycle()
            if (::tts.isInitialized) {
                tts.stop()
                tts.shutdown()
            }
            releaseBeepPlayer()
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            AppLog.d("CountdownWorker: ", "Finishing worker, removing overlay view.")
        }

        return Result.success()
    }

    /**
     * Class: CountdownWorker.kt
     * Method: createForegroundInfo
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [contentText: String]
     * Description: Builds the foreground info wrapper for a status notification.
     **/
    private fun createForegroundInfo(contentText: String, silent: Boolean = false): ForegroundInfo {
        val notification = createNotification(contentText, !silent)

        // For Android 14+ we must specify the service type
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    // --- Helper Functions for Notification and TTS ---
    /**
     * Class: CountdownWorker.kt
     * Method: createNotification
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [contentText: String, isAlerting: Boolean]
     * Description: Creates a notification with the correct channel and priority.
     **/
    private fun createNotification(contentText: String, isAlerting: Boolean): Notification {
        createNotificationChannel()
        val channelId =
            if (isAlerting) ALERTING_NOTIFICATION_CHANNEL_ID else NOTIFICATION_CHANNEL_ID

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(Constants.APP_NAME)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_room)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Set the priority for the builder as well
        if (isAlerting) {
            builder.priority = NotificationCompat.PRIORITY_HIGH
            builder.setVibrate(longArrayOf(0, 500, 250, 500)) // Optional: add vibration
        }

        return builder.build()
    }

    /**
     * Class: CountdownWorker.kt
     * Method: updateNotification
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [contentText: String, isAlerting: Boolean]
     * Description: Updates the ongoing notification with new content.
     **/
    private fun updateNotification(contentText: String, isAlerting: Boolean) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(contentText, isAlerting)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Class: CountdownWorker.kt
     * Method: createNotificationChannel
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Creates the notification channels used by countdown alerts.
     **/
    private fun createNotificationChannel() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /* val name = "Meeting Countdown Channel"
             val descriptionText = "Notifications for meeting end times"
             val importance = NotificationManager.IMPORTANCE_HIGH
             val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                 description = descriptionText
                 setShowBadge(true)
                 lockscreenVisibility = Notification.VISIBILITY_PUBLIC
             }
             val notificationManager =
                 context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.createNotificationChannel(channel)*/
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Your existing channel for silent updates
            val silentChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                Constants.MEETING_COUNT_DOWN,
                NotificationManager.IMPORTANCE_LOW // Use LOW so it doesn't make a sound every second
            ).apply {
                description = Constants.MEETING_COUNT_DOWN_DETAILS
            }
            notificationManager.createNotificationChannel(silentChannel)


            // 2. --- NEW: The High-Importance Channel for the initial alert ---
            val alertingChannel = NotificationChannel(
                ALERTING_NOTIFICATION_CHANNEL_ID,
                Constants.MEETING_COUNT_DOWN,
                NotificationManager.IMPORTANCE_HIGH // Use HIGH to make it a heads-up notification
            ).apply {
                description = Constants.MEETING_COUNT_DOWN_DETAILS
            }
            notificationManager.createNotificationChannel(alertingChannel)

        }
    }

    /**
     * Class: CountdownWorker.kt
     * Method: playBeep
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Plays the notification beep using sonification audio attributes.
     **/
    private fun playBeep() {
        try {
            releaseBeepPlayer()
            val afd = context.resources.openRawResourceFd(R.raw.notification_beep) ?: return
            beepPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { releaseBeepPlayer() }
                setOnErrorListener { _, _, _ ->
                    releaseBeepPlayer()
                    true
                }
                prepareAsync()
            }
            afd.close()
        } catch (e: Exception) {
            AppLog.e("CountdownWorker", "Beep failed: ${e.message}")
            releaseBeepPlayer()
        }
    }

    /**
     * Class: CountdownWorker.kt
     * Method: releaseBeepPlayer
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Stops and releases the MediaPlayer used for beeps.
     **/
    private fun releaseBeepPlayer() {
        beepPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {
                // Ignore stop errors.
            } finally {
                player.release()
            }
        }
        beepPlayer = null
    }

    /**
     * Class: CountdownWorker.kt
     * Method: onInit
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [status: Int]
     * Description: Handles TTS initialization and voice selection.
     **/
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val desiredLocale = Locale.UK
            val suitableVoices = tts.voices?.filter { it.locale == desiredLocale }
            val selectedVoice = suitableVoices?.firstOrNull()
            if (selectedVoice != null) {
                val result = tts.setVoice(selectedVoice)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    AppLog.e("TTS:", "The selected voice is not supported, falling back to default.")
                    tts.language = Locale.US
                } else {
                    AppLog.d("TTS:", "Successfully set voice to: ${selectedVoice.name}")
                }
            } else {
                AppLog.d("TTS:", "UK is not found ")
                tts.language = Locale.US
            }
            isTtsInitialized = true

        } else {
            AppLog.e("TTS: ", "Initialization Failed!")
        }
    }

    /**
     * Class: CountdownWorker.kt
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
     * Class: CountdownWorker.kt
     * Method: createCountdownOverlayView
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Builds and attaches the floating countdown overlay view.
     **/
    private fun createCountdownOverlayView() {
        if (!canRenderOverlay()) {
            AppLog.d("CountdownWorker", "Skipped countdown overlay render subject=$meetingSBJ")
            return
        }
        // Defensive cleanup in case a previous overlay is still attached.
        try {
            countdownOverlayView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
        } catch (_: Exception) {
            // Ignore cleanup errors.
        }

        countdownOverlayView = ComposeView(context).apply {
            lifecycleOwner?.let { owner ->
                this.setViewTreeLifecycleOwner(owner)
                this.setViewTreeSavedStateRegistryOwner(owner)
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Card(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color(0xff1979b7)),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = countdownText,
                            color = Color(0xff1979b7),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Pixels from the top
        }

        countdownOverlayView?.let { view ->
            windowManager.addView(view, params)
            addOverlayView(view)
            AppLog.d(
                "CountdownWorker",
                "Overlay add: countdown size=${overlayViews.size} subject=$meetingSBJ"
            )
        }
    }

    /**
     * Class: CountdownWorker.kt
     * Method: showMeetingFinishedView
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Builds and attaches the meeting finished overlay view.
     **/
    private fun showMeetingFinishedView() {
        if (!canRenderOverlay()) {
            AppLog.d("CountdownWorker", "Skipped finished overlay render subject=$meetingSBJ")
            return
        }
        // Defensive cleanup in case a previous overlay is still attached.
        try {
            finishedOverlayView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
        } catch (_: Exception) {
            // Ignore cleanup errors.
        }

        finishedOverlayView = ComposeView(context).apply {
            lifecycleOwner?.let { owner ->
                this.setViewTreeLifecycleOwner(owner)
                this.setViewTreeSavedStateRegistryOwner(owner)
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Card(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Red
                    ),
                    border = BorderStroke(2.dp, Color.Red),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 6.dp
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Constants.MEETING_FINISHED_MESSAGE,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Pixels from the top
        }

        finishedOverlayView?.let { view ->
            windowManager.addView(view, params)
            addOverlayView(view)
            scheduleOverlayRemoval(view)
            AppLog.d(
                "CountdownWorker",
                "Overlay add: finished size=${overlayViews.size} subject=$meetingSBJ"
            )
        }
    }

    /**
     * Class: CountdownWorker.kt
     * Method: removeOverlayView
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Removes the overlay view if it is initialized.
     **/
    private fun removeOverlayView() {
        hideAndRemoveCountdownOverlayView()
    }

    /**
     * Class: CountdownWorker.kt
     * Method: hideAndRemoveCountdownOverlayView
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Hides and removes the countdown overlay view if attached.
     **/
    private fun hideAndRemoveCountdownOverlayView() {
        countdownOverlayView?.let { view ->
            view.visibility = View.GONE
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            } catch (_: Exception) {
                // View might not be attached; ignore.
            }
            removeOverlayView(view)
            AppLog.d(
                "CountdownWorker",
                "Overlay remove: countdown size=${overlayViews.size} subject=$meetingSBJ"
            )
        }
        countdownOverlayView = null
    }

    /**
     * Class: CountdownWorker.kt
     * Method: hideAndRemoveFinishedOverlayView
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Hides and removes the finished overlay view if attached.
     **/
    private fun hideAndRemoveFinishedOverlayView() {
        finishedOverlayView?.let { view ->
            view.visibility = View.GONE
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            } catch (_: Exception) {
                // View might not be attached; ignore.
            }
            removeOverlayView(view)
            AppLog.d(
                "CountdownWorker",
                "Overlay remove: finished size=${overlayViews.size} subject=$meetingSBJ"
            )
        }
        finishedOverlayView = null
    }

    /**
     * Class: CountdownWorker.kt
     * Method: cleanupOverlayAndLifecycle
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Cleans up overlay view and lifecycle owner on the main thread.
     **/
    private suspend fun cleanupOverlayAndLifecycle() {
        withContext(Dispatchers.Main) {
            // Remove the view and destroy the custom lifecycle
            hideAndRemoveCountdownOverlayView()
            hideAndRemoveFinishedOverlayView()
            lifecycleOwner?.onStop()
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
        }
    }

    /**
     * Class: CountdownWorker.kt
     * Method: scheduleOverlayRemoval
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [view: View]
     * Description: Removes the overlay view after one minute if it is still attached.
     **/
    private fun scheduleOverlayRemoval(view: View) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                clearAllOverlayViews()
                countdownOverlayView = null
                finishedOverlayView = null
                AppLog.d(
                    "CountdownWorker",
                    "Overlay remove: all cleared size=${overlayViews.size} subject=$meetingSBJ"
                )
            } catch (_: Exception) {
                // Ignore cleanup errors.
            }
        }, 60_000L)
    }

    private fun scheduleSingleSweep() {
        Handler(Looper.getMainLooper()).postDelayed({
            clearAllOverlayViews()
        }, 10_000L)
    }
}
