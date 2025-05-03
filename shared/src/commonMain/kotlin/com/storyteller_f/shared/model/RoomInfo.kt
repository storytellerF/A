package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class RoomInfo(
    override val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val name: String,
    val aid: String,
    val creator: PrimaryKey,
    val memberCount: Long,
    val icon: MediaInfo? = null,
    val joinedTime: LocalDateTime? = null,
    val communityId: PrimaryKey? = null,
    val lastRead: PrimaryKey? = null,
) : Identifiable {
    val isPrivate get() = communityId == null
    val isJoined get() = joinedTime != null
    override val objectType: ObjectType
        get() = ObjectType.ROOM

    companion object {
        val EMPTY = RoomInfo(DEFAULT_PRIMARY_KEY, now(), "", "", DEFAULT_PRIMARY_KEY, 0)
    }
}
