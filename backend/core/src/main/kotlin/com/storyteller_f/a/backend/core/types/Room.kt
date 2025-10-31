package com.storyteller_f.a.backend.core.types

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
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

fun RawRoom.toRoomInfo(icon: FileInfo? = null): RoomInfo {
    return RoomInfo(
        room.id,
        room.createdTime,
        room.name,
        room.aid,
        room.creator,
        memberCount ?: 0,
        icon = icon,
        joinedTime = joinedTime,
        communityId = room.communityId,
        lastRead = lastRead,
        latestTopic = latestTopic
    )
}

fun buildUserNotificationRoom(
    user: User,
    adminUid: PrimaryKey
): Room = Room(
    user.notificationId,
    now(),
    "${user.aid}_notification",
    "Notification",
    adminUid,
    null,
    null
)

suspend fun buildMemberForNotificationRoom(
    user: User,
    adminUid: PrimaryKey
): List<Member> = listOf(
    Member(
        SnowflakeFactory.nextId(),
        user.id,
        user.notificationId,
        ObjectType.ROOM,
        user.createdTime,
        MemberStatus.INVITED,
        user.createdTime,
        null,
    ),
    Member(
        SnowflakeFactory.nextId(),
        adminUid,
        user.notificationId,
        ObjectType.ROOM,
        user.createdTime,
        MemberStatus.JOINED,
        null,
        user.createdTime,
    )
)
