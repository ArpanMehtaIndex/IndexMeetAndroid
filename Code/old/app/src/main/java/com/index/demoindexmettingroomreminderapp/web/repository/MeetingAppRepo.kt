package com.index.demoindexmettingroomreminderapp.web.repository


import android.content.Context
import com.google.gson.GsonBuilder
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.utils.AppLog
import com.index.demoindexmettingroomreminderapp.web.client.ApiClient
import com.index.demoindexmettingroomreminderapp.web.model.response.CalendarViewResponse
import com.index.demoindexmettingroomreminderapp.web.model.response.TokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MeetingAppRepo(private val context: Context) {
    /**
     * Class: MeetingAppRepo.KT
     * Method: getAccessToken
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: []
     * Description: Fetches the access token from the API.
     **/
    fun getAccessToken(): Flow<Result<TokenResponse>> = flow {
        try {
            val response = ApiClient.authApiService.getAccessToken(
                clientId = "a45d54b6-f977-465a-8347-23b2cfca9194",
                clientSecret = "Fi88Q~QVFUhLu5oPaGDx5T.5O8YEwmWY.RCeBcBl",
                grantType = "client_credentials",
                scope = "https://graph.microsoft.com/.default"
            )
            emit(Result.success(response))
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            // If the API call fails, emit a failure result with the exception
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO) // Ensure the network call runs on a background thread


    /**
     * Class: MeetingAppRepo.KT
     * Method: getCalendarEvents
     * Created By: Arpan Mehta
     * Created On: 05/02/2026
     * Modified On: 05/02/2026
     * Param: [userEmail: String, start: String, end: String, top: String]
     * Description: Fetches calendar events from the API using the saved access token.
     **/
    fun getCalendarEvents(userEmail: String, start: String, end: String,top:String): Flow<Result<CalendarViewResponse>> = flow {
        try {
            // 1. Get the saved token
            val token = PreferenceHelper.getAccessToken(context)

            if (token == null) {
                // If token is not available, emit an error
                emit(Result.failure(Exception("Access Token not found.")))
                return@flow
            }

            // 2. Format the token for the header
            val formattedToken = "Bearer $token"

            // 3. Make the API call, passing the formatted token
            // NOTE: We can use the simple 'authApiService' now since the interceptor is not needed for this.
            val response = ApiClient.meetingDataApiService.getCalendarView(
                authorization = formattedToken,
                userEmail = userEmail,
                startDateTime = start,
                endDateTime = end,
                top = top
            )
            // --- Pretty Log the JSON Response ---
            val gson = GsonBuilder().setPrettyPrinting().create()
            val prettyJson = gson.toJson(response)
            AppLog.d("MeetingAppRepo", "Calendar Events Response:\n$prettyJson")
            // ----------------------------------------

            emit(Result.success(response))

        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            AppLog.e("MeetingAppRepo", "Error fetching calendar events", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO) // Run on a background
}
