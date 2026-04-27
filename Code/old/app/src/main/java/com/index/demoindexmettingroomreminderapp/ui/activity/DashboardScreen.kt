package com.index.demoindexmettingroomreminderapp.ui.activity

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log

import com.index.demoindexmettingroomreminderapp.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.index.demoindexmettingroomreminderapp.BuildConfig
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.data.Emirate
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.service.MeetingSyncService
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.utils.formatToAmPmCompatible
import com.index.demoindexmettingroomreminderapp.utils.parseEventDateTime
import com.index.demoindexmettingroomreminderapp.viewmodel.MeetingAppViewModel
import com.index.demoindexmettingroomreminderapp.web.UiState
import com.index.demoindexmettingroomreminderapp.web.model.response.CalendarEvent
import com.index.demoindexmettingroomreminderapp.worker.TokenRefreshWorker
import com.index.demoindexmettingroomreminderapp.worker.countdown.CountdownScheduler
import com.index.demoindexmettingroomreminderapp.worker.countdown.CountdownWorker
import com.index.demoindexmettingroomreminderapp.worker.countdown.MeetingEndWorker
import kotlinx.coroutines.delay
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
/**
 * Class: DashboardScreen.kt
 * Method: DashboardScreen
 * Created By: Arpan Mehta
 * Created On: 05/02/2026
 * Modified On: 05/02/2026
 * Param: [meetingAppViewModel: MeetingAppViewModel, onNavigateToSettings: () -> Unit]
 * Description: Renders the dashboard UI and orchestrates meeting state display.
 **/
fun DashboardScreen(
    meetingAppViewModel: MeetingAppViewModel = viewModel()
) {
    val context = LocalContext.current
    val savedEmirate by remember { mutableStateOf(PreferenceHelper.getSelectedEmirate(context)) }
    var showDialog by remember { mutableStateOf(savedEmirate == null) }
    var currentEmirate by remember { mutableStateOf(savedEmirate) }
    val tokenState by meetingAppViewModel.tokenUiState.collectAsState()
    val calendarState by meetingAppViewModel.calendarUiState.collectAsState()
    val activeCountdownMeetingId by meetingAppViewModel.activeCountdownMeetingId.collectAsState()
    val countdownSeconds by meetingAppViewModel.countdownSeconds.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    // showCountdown is now derived from the ViewModel's state
    val showCountdown = activeCountdownMeetingId != null && countdownSeconds > 0
    // This state produces a new Date object every second to trigger recomposition
    // and keep the highlight for the currently active meeting accurate.
    val currentTime by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            delay(1000) // Update every second
        }
    }

    val tts = remember {
        TextToSpeech(context, null)
    }

    var showMeetingFinishedDialog by rememberSaveable { mutableStateOf(false) }
    var finishedMeetingSubject by rememberSaveable { mutableStateOf("") }


    // Clean up the TTS engine when the screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // --- EFFECT 1: Initial data load on app open ---
    LaunchedEffect(Unit) {
        if (!showDialog) {
            meetingAppViewModel.fetchCalendarEvents(loadFromLocalData = false)
            MeetingSyncService.start(context)
        }
    }

    // --- EFFECT 2: Handles the result of the *initial* token fetch from the dialog ---
    LaunchedEffect(tokenState) {
        if (tokenState is UiState.Success) {
            val tokenData = (tokenState as UiState.Success).data
            PreferenceHelper.saveTokenResponse(context, tokenData)
            scheduleTokenRefreshWorker(context)
            meetingAppViewModel.fetchCalendarEvents(loadFromLocalData = false)
            MeetingSyncService.start(context)
        }
    }

    // --- EFFECT 3: Listen for triggers from the MeetingEndWorker ---
    LaunchedEffect(activeCountdownMeetingId) {
        // `snapshotFlow` creates a flow from a composable's state.
        snapshotFlow { activeCountdownMeetingId }
            .collect { newId ->

            }
    }

    // A better way to handle the TTS effect
    LaunchedEffect(Unit) {
        var previousMeetingId: String? = activeCountdownMeetingId
        snapshotFlow { activeCountdownMeetingId }
            .collect { newMeetingId ->
                // Check if the ID changed from something to null
                if (previousMeetingId != null && newMeetingId == null) {
                    val meetingSubject = (calendarState as? UiState.Success)?.data?.value
                        ?.find { it.id == previousMeetingId }?.subject ?: "The"

                    /*val messageToSpeak = "$meetingSubject ${Constants.MEETING_FINISHED_MESSAGE}"
                    tts.speak(messageToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)*/

                    // Also trigger the dialog here
                    finishedMeetingSubject = meetingSubject
                    showMeetingFinishedDialog = true
                }
                previousMeetingId = newMeetingId
            }
    }


    // --- EFFECT 5: Schedule workers when meeting list is successfully fetched ---
    LaunchedEffect(calendarState) {
        if (calendarState !is UiState.Loading) {
            isRefreshing = false
        }
        if (calendarState is UiState.Success) {
            val meetings = (calendarState as UiState.Success).data.value
            CountdownScheduler.scheduleCountdownWorkers(context, meetings)
            meetingAppViewModel.checkAndStartCountdownIfNeeded()
            val activeMeeting = meetings.find { event ->
                val startTime = parseEventDateTime(event.start.dateTime, event.start.timeZone)
                val endTime = parseEventDateTime(event.end.dateTime, event.end.timeZone)
                if (startTime != null && endTime != null) {
                    currentTime.after(startTime) && currentTime.before(endTime)
                } else {
                    false
                }
            }

            /*if (activeMeeting != null) {
                // 1. Schedule future workers
                scheduleMeetingEndWorkers(context, meetings, activeMeeting)
            }
            // 2. Check if a countdown should be running RIGHT NOW
            meetingAppViewModel.checkAndStartCountdownIfNeeded()*/
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Constants.THEME_COLOR,
                    titleContentColor = Color.White
                ),
            )
        },
        floatingActionButton = {
            if (BuildConfig.DEBUG) {
                FloatingActionButton(
                    onClick = { showDialog = true },
                    containerColor = Constants.THEME_COLOR,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Switch meeting room"
                    )
                }
            }
        }
    ) { paddingValues ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                meetingAppViewModel.fetchCalendarEvents(loadFromLocalData = false)
            }
        )
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState),
            contentAlignment = Alignment.Center
        ) {
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // --- Display Countdown Timer if active ---
                if (showCountdown) {
                    val minutes = countdownSeconds / 60
                    val seconds = countdownSeconds % 60
                    Text(
                        text = Constants.MEETING_GOING_TO_END_IN+": ${
                            minutes.toString().padStart(2, '0')
                        }:${seconds.toString().padStart(2, '0')}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }

                currentEmirate?.let {
                    Text(
                        text = "Welcome to " + it.displayName + " "+ Constants.MEETING_ROOM,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = Constants.THEME_COLOR,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (val state = calendarState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator()
                        Text(Constants.FETCHING_MEETING)
                    }

                    is UiState.Success -> {
                        val activeMeeting = state.data.value.find { event ->
                            val startTime = parseEventDateTime(event.start.dateTime, event.start.timeZone)
                            val endTime = parseEventDateTime(event.end.dateTime, event.end.timeZone)
                            if (startTime != null && endTime != null) {
                                currentTime.after(startTime) && currentTime.before(endTime)
                            } else {
                                false
                            }
                        }

                        if (activeMeeting != null) {
                            // If an active meeting is found, display its card
                            Text(
                                Constants.ONGOING_MEETING,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 2.dp),
                                color = Constants.THEME_COLOR,
                            )

                            // Highlight if the meeting is active OR if it's the one with the countdown
                            val isHighlighted =
                                showCountdown && activeMeeting.id == activeCountdownMeetingId
                            val cardBackgroundColor = if (isHighlighted) {
                                Constants.MEETING_HIGH_LIGHT_COLOR
                            } else {
                                // Since it's the only one showing, it should always be highlighted
                                Constants.MEETING_HIGH_LIGHT_COLOR
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 12.dp),
                                elevation = CardDefaults.cardElevation(2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = cardBackgroundColor
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    /*Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Display a consistent icon or number, e.g., "1"
                                        Text(
                                            text = "1",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Constants.THEME_COLOR
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))*/
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            Constants.SUBJECT_LABEL+ activeMeeting.subject,
                                            fontWeight = FontWeight.Bold,
                                            color = Constants.THEME_COLOR
                                        )
                                        Text(Constants.STARTS_LABEL + formatToAmPmCompatible(activeMeeting.start.dateTime, activeMeeting.start.timeZone))
                                        Text(Constants.ENDS_LABEL + formatToAmPmCompatible(activeMeeting.end.dateTime, activeMeeting.end.timeZone))
                                        Text(Constants.ORGANIZER_LABEL + activeMeeting.organizer.emailAddress.name)                            }
                                }
                            }
                        } else {
                            // If no meeting is currently active, show a status message
                            Text(
                                text = Constants.NO_MEETING_IN_PROGRESS,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 32.dp)
                            )
                        }
                    }

                    is UiState.Error -> {
                        Text(
                            Constants.ERROR_IN_FETCHING_MEETING+" ${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is UiState.Idle -> {
                        if (!showDialog) {
                            Text(Constants.PLEASE_WAIT_MEETING_SCHEDULE)
                        }
                    }
                }

            }

            if (showMeetingFinishedDialog) {
                MeetingFinishedDialog(
                    meetingSubject = finishedMeetingSubject,
                    onDismiss = { showMeetingFinishedDialog = false }
                )
            }
            // --- Initial Setup Dialog ---
            if (showDialog) {
                EmiratesSelectionDialog(
                    onDismiss = { showDialog = false },
                    onEmirateSelected = { selectedEmirate ->
                        PreferenceHelper.saveSelectedEmirate(context, selectedEmirate)
                        currentEmirate = selectedEmirate
                        showDialog = false
                        meetingAppViewModel.fetchAccessToken()
                    }
                )
            }
        }
    }
}

/**
 * Class: DashboardScreen.kt
 * Method: scheduleMeetingEndWorkers
 * Created By: Arpan Mehta
 * Created On: 05/02/2026
 * Modified On: 05/02/2026
 * Param: [context: Context, meetings: List<CalendarEvent>, activeMeeting: CalendarEvent]
 * Description: Schedules end-of-meeting workers for countdown handling.
 **/
private fun scheduleMeetingEndWorkers(
    context: Context,
    meetings: List<CalendarEvent>,
    activeMeeting: CalendarEvent
) {
    val workManager = WorkManager.getInstance(context)

    for (event in meetings) {
        workManager.cancelUniqueWork("${Constants.LEGACY_MEETING_WORKER_NAME}${event.id}")
        val now = System.currentTimeMillis()
        val startTime = parseEventDateTime(event.start.dateTime, event.start.timeZone)?.time ?: continue
        val endTime = parseEventDateTime(event.end.dateTime, event.end.timeZone)?.time ?: continue

        val durationInMinutes = TimeUnit.MILLISECONDS.toMinutes(endTime - startTime)

        if (endTime > now && durationInMinutes >= Constants.MIN_TIMER_MINUTE) {
            // Calculate when the countdown should start
            val countdownStartTime = endTime - Constants.TEN_MINUTES_IN_MILLIS
            val initialDelay = countdownStartTime - now

            AppLog.d("MainActivity", "Subject:>>>>>>>>>" + event.subject)

            // Only schedule if the countdown start time is in the future
            if (initialDelay > 0) {
                val inputData = Data.Builder()
                    .putString(MeetingEndWorker.KEY_MEETING_ID, activeMeeting.id)
                    .putString(MeetingEndWorker.KEY_MEETING_SUBJECT, activeMeeting.subject)
                    .putLong(MeetingEndWorker.KEY_MEETING_END_TIME, endTime)
                    .putLong(MeetingEndWorker.KEY_MEETING_START_TIME, startTime)
                    .build()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<MeetingEndWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
//                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS) // <-- Use setInitialDelay
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .build()

                workManager.enqueueUniqueWork(
                    "${Constants.MEETING_END_WORKER_NAME}${event.id}",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d(
                    "Scheduler",
                    "Scheduled worker for ${event.subject} to run in ${initialDelay / 1000}s"
                )
            }
        }
    }
}

/**
 * Class: DashboardScreen.kt
 * Method: EmiratesSelectionDialog
 * Created By: Arpan Mehta
 * Created On: 22/12/2025
 * Modified On: 22/12/2025
 * Param:[onDismiss] Dismiss dialog.
 * Param:[onEmirateSelected] Selected emirate.
 * Description: This is the Emirates selection dialog which will appear when no meeting room is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Class: DashboardScreen.kt
 * Method: EmiratesSelectionDialog
 * Created By: Arpan Mehta
 * Created On: 05/02/2026
 * Modified On: 05/02/2026
 * Param: [onDismiss: () -> Unit, onEmirateSelected: (Emirate) -> Unit]
 * Description: Shows the emirate selection dialog for meeting rooms.
 **/
fun EmiratesSelectionDialog(
    onDismiss: () -> Unit,
    onEmirateSelected: (Emirate) -> Unit
) {
    val emirates = remember { Emirate.entries }
    var expanded by remember { mutableStateOf(false) }
    var selectedEmirate by remember { mutableStateOf(emirates[0]) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Your Meeting Room",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedEmirate.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Meeting Room") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        emirates.forEach { emirate ->
                            DropdownMenuItem(
                                text = { Text(emirate.displayName) },
                                onClick = {
                                    selectedEmirate = emirate
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        onEmirateSelected(selectedEmirate)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Constants.THEME_COLOR, // background color
                        contentColor = Color.White          // text color
                    )
                ) {
                    Text(text = "Submit")
                }
            }
        }
    }
}


/**
 * Class: DashboardScreen.kt
 * Method: MeetingFinishedDialog
 * Created By: Arpan Mehta
 * Created On: 05/02/2026
 * Modified On: 05/02/2026
 * Param: [meetingSubject: String, onDismiss: () -> Unit]
 * Description: Displays a dialog when a meeting finishes.
 **/
@Composable
private fun MeetingFinishedDialog(meetingSubject: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = Constants.MEETING_DIALOG_TITLE, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = "$meetingSubject ${Constants.MEETING_FINISHED_MESSAGE}")
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center

            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.width(120.dp)
                ) {
                    Text(Constants.OK)
                }
            }

        }
    )
}

/**
 * Class: DashboardScreen.kt
 * Method: scheduleTokenRefreshWorker
 * Created By: Arpan Mehta
 * Created On: 05/02/2026
 * Modified On: 05/02/2026
 * Param: [context: Context]
 * Description: Schedules the token refresh worker for background auth renewal.
 **/
private fun scheduleTokenRefreshWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresCharging(true)
        .build()
    val refreshRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
        55,
        TimeUnit.MINUTES
    ).setConstraints(constraints).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        Constants.REFRESH_TOKEN_WORKER_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        refreshRequest
    )
}
