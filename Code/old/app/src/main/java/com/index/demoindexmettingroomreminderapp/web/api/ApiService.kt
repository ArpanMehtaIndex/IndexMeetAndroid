package com.index.demoindexmettingroomreminderapp.web.api


import com.index.demoindexmettingroomreminderapp.data.Constants
import com.index.demoindexmettingroomreminderapp.web.model.response.CalendarViewResponse
import com.index.demoindexmettingroomreminderapp.web.model.response.TokenResponse
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @FormUrlEncoded
    @POST("4474ca65-41c1-4778-bd9a-d12d3b5ba33e/oauth2/v2.0/token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String,
        @Field("scope") scope: String
    ): TokenResponse

    @FormUrlEncoded
    @POST("4474ca65-41c1-4778-bd9a-d12d3b5ba33e/oauth2/v2.0/token")
    fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String,
        @Field("scope") scope: String
    ): Call<TokenResponse>

    /**
     * Fetches the calendar view for a specific user within a time window.
     * This call requires authentication.
     */
    @GET("users/{userEmail}/calendarView")
    suspend fun getCalendarView(
        @Header("Authorization") authorization: String,
        @Path("userEmail") userEmail: String,
        @Query("startDateTime") startDateTime: String,
        @Query("endDateTime") endDateTime: String,
        @Query("top") top: String = Constants.TOP_MEETING_COUNT //Meeting offset No of meeting you want to fetch
    ): CalendarViewResponse
}