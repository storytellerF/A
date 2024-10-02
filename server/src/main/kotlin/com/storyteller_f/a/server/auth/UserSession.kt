package com.storyteller_f.a.server.auth

import com.storyteller_f.shared.type.OKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface UserSession {
    @Serializable
    @SerialName("pending")
    data class Pending(
        val data: String,
        val remote: String,
    ) : UserSession

    @Serializable
    @SerialName("success")
    data class Success(val data: String, val remote: String, val id: OKey) : UserSession
}