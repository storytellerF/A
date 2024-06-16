package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.OKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class RoomInfo(
    override val id: OKey,
    val name: String,
    val aid: String,
    val creator: OKey,
    val icon: MediaInfo? = null,
    val createdTime: LocalDateTime,
    val joinedTime: LocalDateTime? = null,
    val communityId: OKey? = null,
) : Identifiable {
    val isPrivate get() = communityId == null
    val isJoined get() = joinedTime != null
}
