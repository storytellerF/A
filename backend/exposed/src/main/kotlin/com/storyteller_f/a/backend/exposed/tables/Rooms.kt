package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.BaseEntity
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.roomName
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

object Rooms : BaseTable() {
    val name = roomName()
    val icon = customPrimaryKey("icon").nullable()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
}

class Room(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val creator: PrimaryKey,
    val icon: PrimaryKey? = null,
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

data class RoomRawResult(
    val room: Room,
    val joinedTime: LocalDateTime?,
    val topicId: Long?,
    val memberCount: Long
)

fun Room.toRoomInfo(memberCount: Long = 0, joinedTime: LocalDateTime? = null, topicId: Long? = null) = RoomInfo(
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
