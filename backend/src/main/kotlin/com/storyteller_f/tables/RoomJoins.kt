package com.storyteller_f.tables

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object RoomJoins : Table() {
    val uid = ulong("uid").index()
    val roomId = ulong("room_id").index()
    val joinTime = datetime("join_time").index()

    init {
        index("room-join-main", true, uid, roomId)
    }
}

class RoomJoin(val uid: PrimaryKey, val roomId: PrimaryKey, val joinTime: LocalDateTime) {
    companion object {
        fun wrapRow(row: ResultRow): RoomJoin {
            return RoomJoin(row[RoomJoins.uid], row[RoomJoins.roomId], row[RoomJoins.joinTime])
        }
    }
}

suspend fun isRoomJoined(roomId: PrimaryKey, uid: PrimaryKey) = !DatabaseFactory.empty {
    RoomJoins.selectAll().where {
        RoomJoins.roomId eq roomId and (RoomJoins.uid eq uid)
    }
}

fun addRoomJoin(
    room: PrimaryKey,
    id: PrimaryKey
) = RoomJoins.insert {
    it[joinTime] = now()
    it[roomId] = room
    it[uid] = id
}
