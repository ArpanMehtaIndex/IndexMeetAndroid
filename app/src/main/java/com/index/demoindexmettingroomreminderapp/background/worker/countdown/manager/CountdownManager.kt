package com.index.demoindexmettingroomreminderapp.background.worker.countdown.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// A simple data class to hold the countdown trigger info
data class CountdownInfo(val meetingId: String, val endTimeMillis: Long)

object CountdownManager {
    // The flow now emits a CountdownInfo object
    private val _startCountdownForMeeting = MutableSharedFlow<CountdownInfo>()
    val startCountdownForMeeting = _startCountdownForMeeting.asSharedFlow()

    suspend fun triggerCountdown(meetingId: String, endTimeMillis: Long) {
        _startCountdownForMeeting.emit(CountdownInfo(meetingId, endTimeMillis))
    }
}
