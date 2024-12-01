package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

object Rooms : BaseTable() {
    val aid = varchar("aid", ROOM_ID_LENGTH).uniqueIndex()
    val name = varchar("name", ROOM_NAME_LENGTH).index()
    val icon = varchar("icon", ICON_LENGTH).nullable()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
}

class Room(
    val aid: String,
    val name: String,
    val icon: String?,
    val creator: PrimaryKey,
    val communityId: PrimaryKey?,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Room {
            return Room(
                row[Rooms.aid],
                row[Rooms.name],
                row[Rooms.icon],
                row[Rooms.creator],
                row[Rooms.communityId],
                row[Rooms.id],
                row[Rooms.createdTime]
            )
        }

        fun findRoomById(id: PrimaryKey) = Rooms.selectAll().where {
            Rooms.id eq id
        }

        fun findRoomByAId(aid: String) = Rooms.selectAll().where {
            Rooms.aid eq aid
        }
    }
}

suspend fun checkRoomIsPrivate(roomId: PrimaryKey): Result<Boolean?> {
    return DatabaseFactory.first({
        communityId == null
    }, Room::wrapRow) {
        Room.findRoomById(roomId)
    }
}
