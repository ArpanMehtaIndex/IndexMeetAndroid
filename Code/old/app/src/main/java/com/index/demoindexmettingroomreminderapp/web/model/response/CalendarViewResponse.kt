package com.index.demoindexmettingroomreminderapp.web.model.response

import android.location.Location
import com.google.gson.annotations.SerializedName

data class CalendarViewResponse(
    @SerializedName("@odata.context")
    val odatacontext: String,

    @SerializedName("value")
    val value: List<CalendarEvent>
)

data class CalendarEvent(
    @SerializedName("id")
    val id: String,

    @SerializedName("subject")
    val subject: String,

    @SerializedName("isCancelled")
    val isCancelled: Boolean,

    @SerializedName("isAllDay")
    val isAllDay: Boolean,

    @SerializedName("start")
    val start: EventDateTime,

    @SerializedName("end")
    val end: EventDateTime,

    @SerializedName("location")
    val location: Location,

    @SerializedName("locations")
    val locations: List<Location>,

    @SerializedName("organizer")
    val organizer: Organizer,

    @SerializedName("attendees")
    val attendees: List<Attendee>,

    @SerializedName("body")
    val body: Body,

    @SerializedName("onlineMeeting")
    val onlineMeeting: OnlineMeeting? // Nullable as seen in the JSON
)

data class EventDateTime(
    @SerializedName("dateTime")
    val dateTime: String,

    @SerializedName("timeZone")
    val timeZone: String
)

data class Location(@SerializedName("displayName")
                    val displayName: String,

                    @SerializedName("locationType")
                    val locationType: String,

                    @SerializedName("uniqueId")
                    val uniqueId: String?, // Can be null for some location types

                    @SerializedName("locationUri")
                    val locationUri: String? // Nullable
)

data class EmailAddress(
    @SerializedName("name")
    val name: String,

    @SerializedName("address")
    val address: String
)

data class Organizer(
    @SerializedName("emailAddress")
    val emailAddress: EmailAddress
)

data class Attendee(
    @SerializedName("type")
    val type: String,

    @SerializedName("status")
    val status: ResponseStatus,

    @SerializedName("emailAddress")
    val emailAddress: EmailAddress
)

data class ResponseStatus(
    @SerializedName("response")
    val response: String,

    @SerializedName("time")
    val time: String
)

data class Body(
    @SerializedName("contentType")
    val contentType: String,

    @SerializedName("content")
    val content: String
)

data class OnlineMeeting(
    @SerializedName("joinUrl")
    val joinUrl: String
)


