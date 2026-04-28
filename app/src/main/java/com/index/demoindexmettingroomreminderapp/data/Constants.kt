package com.index.demoindexmettingroomreminderapp.data

import androidx.compose.ui.graphics.Color

object Constants {
    const val REFRESH_TOKEN_WORKER_NAME="tokenRefreshWork"
    const val RECOVERY_WORKER_NAME="meetingRecoveryWork"
    const val MEETING_SYNC_WORKER_NAME="meetingSyncWork"
    const val MEETING_STATUS_CHANNEL_ID="meetingStatusChannel"
    const val MEETING_STATUS_NOTIFICATION_ID=54321
    const val LEGACY_MEETING_WORKER_NAME="meeting_end_"
    const val COUNTDOWN_TOKEN_WORKER_NAME="countdown_worker_"
    const val SPLASH_WELCOME_MESSAGE="Welcome to"
    const val APP_NAME="Index Meet"
    const val ONGOING_MEETING="On going meeting"
    const val ONGOING="Ongoing"
    const val MEETING_DIALOG_TITLE="Meeting Concluded"
    const val MEETING_DETAILS="Meeting DETAILS"
    const val MEETING_STATUS="Meeting Status"
    const val MEETING_COUNT_DOWN="Meeting Countdown"
    const val MEETING_COUNT_DOWN_DETAILS="Shows the ongoing countdown for a meeting."

    const val NO_MEETING_IN_PROGRESS="No meeting is currently in progress."
    const val ERROR_IN_FETCHING_MEETING="Error while fetching the meeting list."
    const val PLEASE_WAIT_MEETING_SCHEDULE="Please wait, loading schedule..."
    const val PREPARING_MEETING_STATUS="Preparing meeting status..."
    const val FETCHING_MEETING_SCHEDULE="Please wait, loading schedule..."

    const val STARTS_LABEL = "Starts: "
    const val ENDS_LABEL = "Ends: "
    const val ORGANIZER_LABEL = "Organizer: "
    const val SUBJECT_LABEL = "Subject: "
    const val FETCHING_MEETING="Fetching meetings..."
    const val MEETING_ENDED="Meeting has ended."
    const val MEETING_ROOM="Meeting Room."
    const val OK="OK"
    const val SPLASH_ANIMATION="ic_splash_animation.json"
    const val MEETING_GOING_TO_END_IN="The meeting is scheduled to conclude in"
    const val MEETING_FINISHED_MESSAGE="This meeting has concluded. Please make the room available for the next scheduled meeting. Thank you."
    val THEME_COLOR = Color(0xFF1B68A5)
    val MEETING_HIGH_LIGHT_COLOR = Color(0xFFC8E6C9)
    const val TEN_MINUTES_IN_MILLIS = 10 * 60 * 1000L // 10 minutes in milliseconds
    const val TWENTY_MINUTES_IN_MILLIS = 20 * 60 * 1000L // 10 minutes in milliseconds

    const val MIN_TIMER_MINUTE=10
    const val TOP_MEETING_COUNT="1000"
    const val MINUTES="minutes"


}
