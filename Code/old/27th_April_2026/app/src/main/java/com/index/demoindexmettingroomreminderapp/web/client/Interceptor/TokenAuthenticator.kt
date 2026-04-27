package com.index.demoindexmettingroomreminderapp.web.client.Interceptor

import android.content.Context
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.web.client.ApiClient
import com.index.demoindexmettingroomreminderapp.web.model.response.TokenResponse
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val context: Context) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Get the currently saved token to see if we need to refresh.
        val currentToken = PreferenceHelper.getAccessToken(context)
        if (currentToken == null) {
            // If there's no token, we can't refresh.
            return null
        }

        // Use synchronized block to prevent multiple threads from refreshing the token at the same time.
        synchronized(this) {
            val updatedToken = PreferenceHelper.getAccessToken(context)
            // Check if another thread has already refreshed the token while this one was waiting.
            if (updatedToken != null && updatedToken != currentToken) {
                // Token was already refreshed. Retry the request with the new token.
                return response.request
                    .newBuilder()
                    .header("Authorization", "Bearer $updatedToken")
                    .build()
            }

            // We need to refresh the token.
            val tokenResponse = refreshToken()

            if (tokenResponse != null) {
                // Save the new token and its expiration.
                PreferenceHelper.saveTokenResponse(context, tokenResponse)
                // Retry the original failed request with the new token.
                return response.request
                    .newBuilder()
                    .header("Authorization", "Bearer ${tokenResponse.accessToken}")
                    .build()
            }
        }
        // If we can't refresh the token, return null. The original request will fail.
        return null
    }

    private fun refreshToken(): TokenResponse? {
        return try {
            val tokenCall = ApiClient.authApiService.refreshAccessToken(
                clientId = "a45d54b6-f977-465a-8347-23b2cfca9194",
                clientSecret = "Fi88Q~QVFUhLu5oPaGDx5T.5O8YEwmWY.RCeBcBl",
                grantType = "client_credentials",
                scope = "https://graph.microsoft.com/.default"
            )
            val response = tokenCall.execute() // Synchronous call
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
