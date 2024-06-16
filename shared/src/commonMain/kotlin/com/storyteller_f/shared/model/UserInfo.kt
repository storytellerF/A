package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.OKey
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    override val id: OKey,
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
