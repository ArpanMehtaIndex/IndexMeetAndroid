package com.index.demoindexmettingroomreminderapp.web.model.response

import com.google.gson.annotations.SerializedName

/**
 * Data class to model the JSON response from the Microsoft OAuth2 token endpoint.
 */
data class TokenResponse(

    @SerializedName("token_type")
    val tokenType: String,

    @SerializedName("expires_in")
    val expiresIn: Int,

    @SerializedName("ext_expires_in")
    val extExpiresIn: Int,

    @SerializedName("access_token")
    val accessToken: String
)