package com.index.demoindexmettingroomreminderapp.utils

import android.util.Log
/**
 * A generic logging utility class that wraps Android's Log class.
 * It automatically uses a consistent tag and can be disabled for release builds.
 */
object AppLog {

    // A constant tag for all logs from this app. Makes filtering in Logcat easy.
    private const val TAG = "IndexMeetingApp"

    /**
     * Logs a debug message.
     * @param message The message to log.
     */
    fun d(tag: String,message: String) {
        Log.d(tag, message)
    }

    /**
     * Logs an error message.
     * @param message The message to log.
     * @param throwable An optional throwable (exception) to log with the message.
     */
    fun e(tag: String,message: String, throwable: Throwable? = null) {
        Log.e(tag, message)
    }

    /**
     * Logs an informational message.
     * @param message The message to log.
     */
    fun i(message: String) {
        Log.i(TAG, message)
    }

    /**
     * Logs a verbose message.
     * @param message The message to log.
     */
    fun v(message: String) {
        Log.v(TAG, message)
    }

    /**
     * Logs a warning message.
     * @param message The message to log.
     */
    fun w(message: String) {
        Log.w(TAG, message)
    }
}
