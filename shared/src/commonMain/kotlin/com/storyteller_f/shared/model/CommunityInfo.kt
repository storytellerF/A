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
    val memberCount: Long,
    val icon: MediaInfo? = null,
    val poster: MediaInfo? = null,
    val joinTime: LocalDateTime? = null,
    val extension: Extension? = null,
) {
    val isJoined get() = joinTime != null

    @Serializable
    data class Extension(val targetUserJoinedTime: LocalDateTime? = null)

    companion object {
        val EMPTY = CommunityInfo(DEFAULT_PRIMARY_KEY, "", "", DEFAULT_PRIMARY_KEY, now(), 0, null, null, now())
    }
}
