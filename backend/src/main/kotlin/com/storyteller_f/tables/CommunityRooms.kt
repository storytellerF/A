package com.storyteller_f.tables

import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll

object CommunityRooms : Table() {
    val communityId = ulong("community").index()
    val roomId = ulong("room").index()

    init {
        index("community-room-main", true, communityId, roomId)
    }
}

fun checkRoomIsPrivate(roomId: PrimaryKey): Boolean {
    return CommunityRooms.selectAll().where {
        CommunityRooms.roomId eq roomId
    }.limit(1).count() == 0L
}
