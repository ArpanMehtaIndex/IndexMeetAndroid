package com.index.demoindexmettingroomreminderapp.web.client.Interceptor

import android.content.Context
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Get the original request
        val originalRequest = chain.request()

        // Get the access token from SharedPreferences
        val accessToken = PreferenceHelper.getAccessToken(context)

        // Build the new request with the Authorization header
        val requestBuilder = originalRequest.newBuilder()
        if (accessToken != null) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }

        return chain.proceed(requestBuilder.build())
    }
}

/*class TokenAuthenticator(
    private val context: Context
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // We need to check if the response code is 401 Unauthorized
        if (response.code == 401) {
            // Synchronously refresh the token
            val newToken = refreshToken()

            if (newToken != null) {
                // Save the new token response
                PreferenceHelper.saveTokenResponse(context, newToken)
                // Retry the failed request with the new token
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${newToken.accessToken}")
                    .build()
            }
        }
        return null // Return null if we can't refresh the token, which will cause the original request to fail.
    }

    private fun refreshToken(): TokenResponse? {
        // IMPORTANT: This network call must be SYNCHRONOUS
        // We're using another Retrofit client here to avoid a circular dependency with interceptors.
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(logging).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(ApiService::class.java)

        return try {
            val call = service.refreshAccessToken(
                clientId = "a45d54b6-f977-465a-8347-23b2cfca9194",
                clientSecret = "Fi88Q~QVFUhLu5oPaGDx5T.5O8YEwmWY.RCeBcBl",
                grantType = "client_credentials",
                scope = "https://graph.microsoft.com/.default"
            )
            // Execute the call synchronously
            val response = call.execute()
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}*/
