package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Room(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val creator: PrimaryKey,
    val icon: PrimaryKey? = null,
    val communityId: PrimaryKey? = null,
) {
    companion object
}

data class RawRoom(
    val room: Room,
    val joinedTime: LocalDateTime? = null,
    val lastRead: PrimaryKey? = null,
    val memberCount: Long? = null,
    val latestTopic: PrimaryKey? = null,
)

fun Room.toRoomInfo(
    memberCount: Long = 0,
    joinedTime: LocalDateTime? = null,
    topicId: PrimaryKey? = null
) = RoomInfo(
    id,
    createdTime,
    name,
    aid,
    creator,
    memberCount,
    joinedTime = joinedTime,
    communityId = communityId,
    lastRead = topicId
)
