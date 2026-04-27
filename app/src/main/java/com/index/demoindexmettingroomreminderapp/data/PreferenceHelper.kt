package com.index.demoindexmettingroomreminderapp.data

import android.content.Context
import android.content.SharedPreferences
import com.index.demoindexmettingroomreminderapp.web.model.response.TokenResponse

object PreferenceHelper {

    private const val PREFS_NAME = "MeetingRoomPrefs"
    private const val KEY_SELECTED_EMIRATE = "selected_emirate"
    private const val KEY_ACCESS_TOKEN = "access_token" // New key for the token
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at" // New key

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Save the enum's unique name
    fun saveSelectedEmirate(context: Context, emirate: Emirate) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_SELECTED_EMIRATE, emirate.name) // Store enum name (e.g., "DUBAI")
        editor.apply()
    }

    // Retrieve the enum by its saved name
    fun getSelectedEmirate(context: Context): Emirate? {
        val emirateName = getPreferences(context).getString(KEY_SELECTED_EMIRATE, null)
        return emirateName?.let {
            try {
                Emirate.valueOf(it) // Convert string back to enum
            } catch (e: IllegalArgumentException) {
                null // Handle cases where the saved value is invalid
            }
        }
    }

    fun saveAccessToken(context: Context, token: String) {
        val editor = getPreferences(context).edit()
        editor.putString(KEY_ACCESS_TOKEN, token)
        editor.apply()
    }

    /**
     * Retrieves the saved access token from SharedPreferences.
     */
    fun getAccessToken(context: Context): String? {
        return getPreferences(context).getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Saves the fetched access token and calculates its expiration time.
     */
    fun saveTokenResponse(context: Context, tokenResponse: TokenResponse) {
        val editor = getPreferences(context).edit()
        val expiresInSeconds = tokenResponse.expiresIn
        // Calculate the timestamp when the token will expire
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)

        editor.putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
        editor.putLong(KEY_TOKEN_EXPIRES_AT, expiresAt) // Save the expiration timestamp
        editor.apply()
    }

    /**
     * Checks if the stored token is expired.
     * We'll add a 60-second buffer to be safe.
     */
    fun isTokenExpired(context: Context): Boolean {
        val expiresAt = getPreferences(context).getLong(KEY_TOKEN_EXPIRES_AT, 0)
        if (expiresAt == 0L) return true // No token saved
        return System.currentTimeMillis() >= (expiresAt - 60_000) // Check if expired or about to expire
    }

    /**
     * Clears all token-related data.
     */
    fun clearToken(context: Context) {
        val editor = getPreferences(context).edit()
        editor.remove(KEY_ACCESS_TOKEN)
        editor.remove(KEY_TOKEN_EXPIRES_AT)
        editor.apply()
    }
}