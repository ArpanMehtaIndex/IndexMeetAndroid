package com.index.demoindexmettingroomreminderapp.web.client

import android.content.Context
import com.index.demoindexmettingroomreminderapp.web.api.ApiService
import com.index.demoindexmettingroomreminderapp.web.client.Interceptor.AuthInterceptor
import com.index.demoindexmettingroomreminderapp.web.client.Interceptor.TokenAuthenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // Base URL for fetching the token
    const val AUTH_BASE_URL = "https://login.microsoftonline.com/"

    // Base URL for all authenticated Microsoft Graph API calls
    const val GRAPH_API_BASE_URL = "https://graph.microsoft.com/v1.0/"

    // Main Retrofit instance for authenticated calls (Graph API)
    private lateinit var graphApiRetrofit: Retrofit

    // A separate, lazy-initialized client for making the initial token call.
    // This uses AUTH_BASE_URL and does NOT have interceptors to avoid loops.
    private val authRetrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Initializes the main Retrofit client for the Graph API.
     * This client will be used for all API calls that require an "Authorization" header.
     * This method MUST be called once from the Application class.
     */
    fun initialize(context: Context) {
        if (::graphApiRetrofit.isInitialized) return

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // The main OkHttp client includes the AuthInterceptor for adding the token.
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .authenticator(TokenAuthenticator(context))
            .addInterceptor(loggingInterceptor)
            .build()

        // Build the main Retrofit instance for the Graph API using the authenticated client.
        graphApiRetrofit = Retrofit.Builder()
            .baseUrl(GRAPH_API_BASE_URL) // <-- Use the correct Graph API base URL here
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provides the ApiService for fetching the initial token.
     * It uses the client configured with AUTH_BASE_URL.
     */
    val authApiService: ApiService by lazy {
        authRetrofit.create(ApiService::class.java)
    }

    /**
     * Provides an ApiService for making authenticated calls to the Graph API.
     * This uses the client configured with GRAPH_API_BASE_URL and the AuthInterceptor.
     */
    val meetingDataApiService: ApiService by lazy {
        if (!::graphApiRetrofit.isInitialized) {
            throw IllegalStateException("ApiClient must be initialized in Application class first.")
        }
        graphApiRetrofit.create(ApiService::class.java)
    }
}
