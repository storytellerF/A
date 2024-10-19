package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
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
}
