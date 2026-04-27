package com.index.demoindexmettingroomreminderapp.utils

import java.text.SimpleDateFormat

import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses an ISO 8601 UTC date-time string into a user-friendly "hh:mm a" format.
 * This version is compatible with all Android API levels.
 */
fun formatToAmPmCompatible(dateTimeString: String, timeZoneId: String? = null): String {
    return try {
        val inputFormatWithZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSX", Locale.ENGLISH)
        inputFormatWithZ.timeZone = TimeZone.getTimeZone("UTC")

        val inputFormatWithoutZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH)
        // If the API doesn't include a timezone, use the provided timeZoneId when available.
        val sourceTimeZone = if (!timeZoneId.isNullOrBlank()) {
            TimeZone.getTimeZone(timeZoneId)
        } else {
            TimeZone.getDefault()
        }
        inputFormatWithoutZ.timeZone = sourceTimeZone

        val outputFormat = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        outputFormat.timeZone = TimeZone.getDefault()

        val date = try {
            inputFormatWithZ.parse(dateTimeString)
        } catch (e: java.text.ParseException) {
            inputFormatWithoutZ.parse(dateTimeString)
        }
        outputFormat.format(date!!)
    } catch (e: Exception) {
        e.printStackTrace()
        "Invalid Time"
    }
}

/**
 * Parses a UTC date-time string from the API into a Date object for comparisons.
 * This is compatible with all Android API levels.
 */
fun parseUtcDateTime(dateTimeString: String): Date? {
    val inputFormatWithZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSX", Locale.ENGLISH)
    inputFormatWithZ.timeZone = TimeZone.getTimeZone("UTC")

    val inputFormatWithoutZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH)
    // If the API doesn't include a timezone, assume the time is already in local time.
    inputFormatWithoutZ.timeZone = TimeZone.getDefault()

    return try {
        try {
            inputFormatWithZ.parse(dateTimeString)
        } catch (e: java.text.ParseException) {
            inputFormatWithoutZ.parse(dateTimeString)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Parses an event date-time string using the event's timeZone when no offset is present.
 */
fun parseEventDateTime(dateTimeString: String, timeZoneId: String?): Date? {
    val inputFormatWithZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSX", Locale.ENGLISH)
    inputFormatWithZ.timeZone = TimeZone.getTimeZone("UTC")

    val inputFormatWithoutZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH)
    val sourceTimeZone = if (!timeZoneId.isNullOrBlank()) {
        TimeZone.getTimeZone(timeZoneId)
    } else {
        TimeZone.getDefault()
    }
    inputFormatWithoutZ.timeZone = sourceTimeZone

    return try {
        try {
            inputFormatWithZ.parse(dateTimeString)
        } catch (e: java.text.ParseException) {
            inputFormatWithoutZ.parse(dateTimeString)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
