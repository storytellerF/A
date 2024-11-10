package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CommunityInfo(
    val id: PrimaryKey,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val createTime: LocalDateTime,
    val icon: MediaInfo? = null,
    val poster: MediaInfo? = null,
    val joinTime: LocalDateTime? = null,
) {
    val isJoined get() = joinTime != null

    companion object {
        val EMPTY = CommunityInfo(DEFAULT_PRIMARY_KEY, "", "", DEFAULT_PRIMARY_KEY, now(), null, null, now())
    }
}
