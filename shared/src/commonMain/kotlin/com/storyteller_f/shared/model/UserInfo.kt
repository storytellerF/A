package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    override val id: PrimaryKey,
    val address: String,
    val acg: Long,
    val aid: String?,
    val nickname: String,
    val avatar: MediaInfo?
) : Identifiable {
    companion object {
        val EMPTY = UserInfo(0u, "", 0, "", "", null)
    }
}

@Serializable
data class LoginUser(
    val privateKey: String,
    val publicKey: String,
    val address: String,
    val signature: String?,
    val data: String,
    val user: UserInfo
)
