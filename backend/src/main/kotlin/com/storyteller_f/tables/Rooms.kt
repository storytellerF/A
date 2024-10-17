package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.type.OKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

object Rooms : BaseTable() {
    val aid = varchar("aid", ROOM_ID_LENGTH).uniqueIndex()
    val name = varchar("name", ROOM_NAME_LENGTH).index()
    val icon = varchar("icon", ICON_LENGTH).nullable()
    val creator = ulong("creator").index()
}

class Room(
    val aid: String,
    val name: String,
    val icon: String?,
    val creator: OKey,
    id: OKey,
    createdTime: LocalDateTime
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Room {
            return Room(
                row[Rooms.aid],
                row[Rooms.name],
                row[Rooms.icon],
                row[Rooms.creator],
                row[Rooms.id],
                row[Rooms.createdTime]
            )
        }
    }
}

fun findRoomById(id: OKey): ResultRow? {
    return Rooms.selectAll().where {
        Rooms.id eq id
    }.limit(1).firstOrNull()
}

fun findRoomByAId(aid: String): ResultRow? {
    return Rooms.selectAll().where {
        Rooms.aid eq aid
    }.limit(1).firstOrNull()
}
