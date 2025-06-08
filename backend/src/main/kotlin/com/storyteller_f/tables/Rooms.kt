package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

object Rooms : BaseTable() {
    val name = roomName()
    val icon = roomIcon()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
}

class Room(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val creator: PrimaryKey,
    val icon: String? = null,
    val communityId: PrimaryKey? = null,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Room {
            return with(Rooms) {
                Room(
                    row[id],
                    row[createdTime],
                    row[Aids.value],
                    row[name],
                    row[creator],
                    row[icon],
                    row[communityId]
                )
            }
        }

        fun findRoomById(id: PrimaryKey) = Rooms.selectAll().where {
            Rooms.id eq id
        }
    }
}

data class RoomRawResult(val roomInfo: RoomInfo, val icon: String?)

fun mapRoomInfo(it: ResultRow): RoomRawResult {
    val joinedTime = it.getOrNull(MemberJoins.joinedTime)
    val topicId = it.getOrNull(UserTopicReads.topicId)
    val room = Room.wrapRow(it)
    return RoomRawResult(room.toRoomInfo(0, joinedTime, topicId), room.icon)
}

fun Room.toRoomInfo(memberCount: Long, joinedTime: LocalDateTime?, topicId: Long?) = RoomInfo(
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
