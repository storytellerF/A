package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.OKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CommunityInfo(
    val id: OKey,
    val aid: String,
    val name: String,
    val owner: OKey,
    val createTime: LocalDateTime,
    val icon: MediaInfo? = null,
    val poster: MediaInfo? = null,
    val joinTime: LocalDateTime? = null,
) {
    val isJoined get() = joinTime != null
}

