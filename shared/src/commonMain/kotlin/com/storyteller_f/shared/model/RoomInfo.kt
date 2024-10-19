package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class RoomInfo(
    override val id: PrimaryKey,
    val name: String,
    val aid: String,
    val creator: PrimaryKey,
    val icon: MediaInfo? = null,
    val createdTime: LocalDateTime,
    val joinedTime: LocalDateTime? = null,
    val communityId: PrimaryKey? = null,
) : Identifiable {
    val isPrivate get() = communityId == null
    val isJoined get() = joinedTime != null
}
