package com.index.demoindexmettingroomreminderapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.index.demoindexmettingroomreminderapp.background.worker.countdown.manager.CountdownInfo
import com.index.demoindexmettingroomreminderapp.background.worker.countdown.manager.CountdownManager
import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.data.Emirate
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.utils.parseEventDateTime
import com.index.demoindexmettingroomreminderapp.web.UiState
import com.index.demoindexmettingroomreminderapp.web.model.response.CalendarViewResponse
import com.index.demoindexmettingroomreminderapp.web.model.response.TokenResponse
import com.index.demoindexmettingroomreminderapp.web.repository.MeetingAppRepo

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MeetingAppViewModel(application: Application) : AndroidViewModel(application) {
    private val meetingAppRepo = MeetingAppRepo(application.applicationContext)

    // ... (tokenUiState remains the same)
    private val _tokenUiState = MutableStateFlow<UiState<TokenResponse>>(UiState.Idle)
    val tokenUiState: StateFlow<UiState<TokenResponse>> = _tokenUiState.asStateFlow()

    private val _activeCountdownMeetingId = MutableStateFlow<String?>(null)
    val activeCountdownMeetingId: StateFlow<String?> = _activeCountdownMeetingId.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private var countdownJob: Job? = null


    private val _calendarUiState = MutableStateFlow<UiState<CalendarViewResponse>>(UiState.Idle)
    val calendarUiState: StateFlow<UiState<CalendarViewResponse>> = _calendarUiState.asStateFlow()

    init {
        // Listen for triggers from the worker
        viewModelScope.launch {
            CountdownManager.startCountdownForMeeting.collect { countdownInfo ->
                startOrUpdateCountdown(countdownInfo.endTimeMillis, countdownInfo.meetingId)
            }
        }
    }

    /**
     * Class: MeetingAppViewModel.KT
     * Method: checkAndStartCountdownIfNeeded
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Starts the countdown if the active meeting is within the last 10 minutes.
     **/
    fun checkAndStartCountdownIfNeeded() {
        val calendarState = _calendarUiState.value
        if (calendarState is UiState.Success) {
            val meetings = calendarState.data.value
            val now = System.currentTimeMillis()

            val meetingToTrack = meetings.find {
                val endTime = parseEventDateTime(it.end.dateTime, it.end.timeZone)?.time ?: 0L
                val tenMinutesBeforeEnd = endTime - Constants.TEN_MINUTES_IN_MILLIS
                // Check if we are inside the 10-minute window
                now in tenMinutesBeforeEnd until endTime
            }

            if (meetingToTrack != null) {
                val endTime = parseEventDateTime(meetingToTrack.end.dateTime, meetingToTrack.end.timeZone)?.time ?: 0L
                startOrUpdateCountdown(endTime, meetingToTrack.id)
            }
        }
    }

    /**
     * Class: MeetingAppViewModel.KT
     * Method: startOrUpdateCountdown
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [endTimeMillis: Long, meetingId: String]
     * Description: Starts a new countdown or updates the existing countdown.
     **/
    private fun startOrUpdateCountdown(endTimeMillis: Long, meetingId: String) {
        countdownJob?.cancel() // Cancel any existing countdown
        _activeCountdownMeetingId.value = meetingId

        countdownJob = viewModelScope.launch {
            var remainingMillis = endTimeMillis - System.currentTimeMillis()

            while (remainingMillis > 0) {
                _countdownSeconds.value = (remainingMillis / 1000).toInt()
                delay(1000)
                remainingMillis = endTimeMillis - System.currentTimeMillis()
            }

            // Countdown finished
            _countdownSeconds.value = 0
            _activeCountdownMeetingId.value = null
        }
    }

    /**
     * Class: MeetingAppViewModel.KT
     * Method: startCountdown
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [info: CountdownInfo]
     * Description: Starts a countdown based on the provided countdown info.
     **/
    private fun startCountdown(info: CountdownInfo) {
        countdownJob?.cancel() // Cancel any existing countdown
        _activeCountdownMeetingId.value = info.meetingId

        countdownJob = viewModelScope.launch {
            val endTime = info.endTimeMillis
            var remainingMillis = endTime - System.currentTimeMillis()

            while (remainingMillis > 0) {
                _countdownSeconds.value = (remainingMillis / 1000).toInt()
                delay(1000)
                remainingMillis = endTime - System.currentTimeMillis()
            }

            // Countdown finished
            _countdownSeconds.value = 0
            _activeCountdownMeetingId.value = null
            // The TTS logic will now be handled in the UI based on this state change
        }
    }

    /**
     * Class: MeetingAppViewModel.KT
     * Method: fetchAccessToken
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Fetches and stores the access token from the API.
     **/
    fun fetchAccessToken() {
        _tokenUiState.value = UiState.Loading

        viewModelScope.launch {
            meetingAppRepo.getAccessToken()
                .catch { exception ->
                    _tokenUiState.value = UiState.Error(exception.message ?: "An unknown error occurred")
                }
                .collect { result ->
                    result.fold(
                        onSuccess = { tokenResponse ->
                            _tokenUiState.value = UiState.Success(tokenResponse)
                        },
                        onFailure = { exception ->
                            _tokenUiState.value = UiState.Error(exception.message ?: "Failed to fetch token")
                        }
                    )
                }
        }
    }

    /**
     * Class: MeetingAppViewModel.KT
     * Method: fetchCalendarEvents
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [loadFromLocalData: Boolean]
     * Description: Loads calendar events from the API or local assets.
     **/
    fun fetchCalendarEvents(loadFromLocalData: Boolean = false) {
        _calendarUiState.value = UiState.Loading
        viewModelScope.launch {
            if (loadFromLocalData) {
                try {
                    val inputStream = getApplication<Application>().assets.open("MeetingRoomSampleJson.json")
                    val reader = InputStreamReader(inputStream)
                    val response = Gson().fromJson(reader, CalendarViewResponse::class.java)
                    AppLog.d(
                        "MeetingAppViewModel",
                        "Loaded local calendar data: ${response.value.size} events"
                    )
                    _calendarUiState.value = UiState.Success(response)
                } catch (e: Exception) {
                    AppLog.e("MeetingAppViewModel", "Failed to load local data: ${e.message}")
                    _calendarUiState.value = UiState.Error("Failed to load local data: ${e.message}")
                }
            } else {
                val selectedEmirate = PreferenceHelper.getSelectedEmirate(getApplication())
                val userEmail = when (selectedEmirate) {
                    Emirate.ABU_DHABI -> "auh@index.ae"
                    Emirate.DUBAI -> "dubai@index.ae"
                    Emirate.SHARJAH -> "sharjah@index.ae"
                    Emirate.RAS_AL_KHAIMAH -> "rak@index.ae"
                    Emirate.DESIGN -> "design.meeting@index.ae"
                    else -> {
                        // Fallback or error case if no emirate is selected
                        _calendarUiState.value = UiState.Error("No meeting room selected.")
                        return@launch
                    }
                }

                val calendar = Calendar.getInstance()
                val utcTimeZone = TimeZone.getTimeZone("UTC")
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = utcTimeZone
                }

                calendar.set(Calendar.HOUR_OF_DAY, 7)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDateTime = isoFormat.format(calendar.time)

                // Set to end of the day (e.g., 10:00 PM UTC)
                calendar.set(Calendar.HOUR_OF_DAY, 22)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val endDateTime = isoFormat.format(calendar.time)

                AppLog.d(
                    "MeetingAppViewModel",
                    "Fetching calendar events for $userEmail from $startDateTime to $endDateTime"
                )

                meetingAppRepo.getCalendarEvents(userEmail, startDateTime, endDateTime, Constants.TOP_MEETING_COUNT)
                    .catch { e ->
                        AppLog.e("MeetingAppViewModel", "Calendar fetch error: ${e.message}")
                        _calendarUiState.value = UiState.Error(e.message ?: "An unknown error occurred")
                    }
                    .collect { result ->
                        result.fold(
                            onSuccess = { response ->
                                val firstEvent = response.value.firstOrNull()
                                AppLog.d(
                                    "MeetingAppViewModel",
                                    "Calendar response: ${response.value.size} events. " +
                                            "First: ${firstEvent?.subject ?: "none"} " +
                                            "(${firstEvent?.start?.dateTime ?: "n/a"} - ${firstEvent?.end?.dateTime ?: "n/a"})"
                                )
                                AppLog.d(
                                    "MeetingAppViewModel",
                                    "Calendar size:>>>>>>> ${response.value.size}")
                                // Log a compact list of events (limit to avoid log spam)
                                val maxLogEvents = 20
                                response.value.take(maxLogEvents).forEachIndexed { index, event ->
                                    AppLog.d(
                                        "MeetingAppViewModel",
                                        "Event[$index]: id=${event.id}, subject=${event.subject}, " +
                                                "start=${toDubaiTime(event.start.dateTime)}, " +
                                                "end=${toDubaiTime(event.end.dateTime)}"
                                    )
                                }
                                if (response.value.size > maxLogEvents) {
                                    AppLog.d(
                                        "MeetingAppViewModel",
                                        "Event list truncated: ${response.value.size - maxLogEvents} more not logged"
                                    )
                                }
                                // Pretty JSON log (can be large; keep as single log entry)
                                val prettyJson = GsonBuilder()
                                    .setPrettyPrinting()
                                    .create()
                                    .toJson(response)
                                AppLog.d("MeetingAppViewModel", "Calendar response JSON:\n$prettyJson")
                                _calendarUiState.value = UiState.Success(response)
                            },
                            onFailure = { e ->
                                AppLog.e("MeetingAppViewModel", "Calendar fetch failed: ${e.message}")
                                _calendarUiState.value = UiState.Error(e.message ?: "Failed to fetch calendar events")
                            }
                        )
                    }
            }
        }
    }

    /**
     * Class: MeetingAppViewModel.KT
     * Method: toDubaiTime
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [isoDateTime: String]
     * Description: Converts a UTC ISO date string to Dubai time for logging.
     **/
    private fun toDubaiTime(isoDateTime: String): String {
        // Supports inputs like 2026-02-03T05:00:00.0000000 or 2026-02-03T05:00:00Z
        val utc = TimeZone.getTimeZone("UTC")
        val dubai = TimeZone.getTimeZone("Asia/Dubai")

        val cleaned = isoDateTime
            .removeSuffix("Z")
            .let { raw ->
                val dot = raw.indexOf('.')
                if (dot >= 0) {
                    // Keep up to 3 fractional digits for SimpleDateFormat parsing
                    val prefix = raw.substring(0, dot)
                    val fraction = raw.substring(dot + 1).take(3).padEnd(3, '0')
                    "$prefix.$fraction"
                } else {
                    raw
                }
            }

        val inputFormat = if (cleaned.contains('.')) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        } else {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        }
        inputFormat.timeZone = utc

        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        outputFormat.timeZone = dubai

        return runCatching {
            val date = inputFormat.parse(cleaned) ?: return isoDateTime
            outputFormat.format(date)
        }.getOrElse { isoDateTime }
    }
}
