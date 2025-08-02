package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.roomName
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.selectAll

object Rooms : BaseTable() {
    val name = roomName()
    val icon = customPrimaryKey("icon").nullable()
    val creator = customPrimaryKey("creator").index()
    val communityId = customPrimaryKey("community_id").index().nullable()
}

fun Room.Companion.wrapRow(row: ResultRow): Room {
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

fun Room.Companion.findRoomById(id: PrimaryKey) = Rooms.selectAll().where {
    Rooms.id eq id
}
