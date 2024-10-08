package com.storyteller_f.a.server.service

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.backend
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.tables.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and


suspend fun RoutingContext.getRoomPubKeys(
    it: OKey,
    id: OKey
) = if (isRoomJoined(it, id)) {
    runCatching {
        val data = DatabaseFactory.dbQuery {
            getUserPubKeysInRoom(it)
        }
        ServerResponse(data, 10)
    }
} else {
    Result.failure(ForbiddenException("未加入私有聊天室"))
}


suspend fun RoutingContext.joinRoom(
    room: OKey,
    id: OKey
) = runCatching {
    if (DatabaseFactory.dbQuery {
            checkRoomIsPrivate(room)
        }) {
        null
    } else {
        DatabaseFactory.dbQuery {
            addRoomJoin(room, id)
        }
        Unit
    }
}


suspend fun searchRooms(word: String, uid: OKey?): Result<ServerResponse<RoomInfo>> {
    return runCatching {
        roomsResponse(DatabaseFactory.dbQuery {
            val baseJoin = Rooms.join(CommunityRooms, JoinType.INNER, Rooms.id, CommunityRooms.roomId)
            val baseOp = Op.build {
                Rooms.name like "%$word%"
            }
            val baseFields = Rooms.fields + CommunityRooms.communityId
            if (uid != null) {
                baseJoin
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(baseFields + RoomJoins.joinTime)
                    .where {
                        baseOp and (RoomJoins.uid eq uid)
                    }
            } else {
                baseJoin
                    .select(baseFields)
                    .where {
                        baseOp
                    }
            }.map(::mapRoomInfo)
        })
    }
}

suspend fun searchJoinedRooms(uid: OKey): Result<ServerResponse<RoomInfo>> {
    return runCatching {
        roomsResponse(DatabaseFactory.dbQuery {
            RoomJoins.join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
                .join(CommunityRooms, JoinType.LEFT, RoomJoins.roomId, CommunityRooms.roomId)
                .select(Rooms.fields + RoomJoins.joinTime + CommunityRooms.communityId)
                .where {
                    RoomJoins.uid eq uid
                }.map {
                    mapRoomInfo(it)
                }
        })
    }
}

fun mapRoomInfo(it: ResultRow): Pair<RoomInfo, String?> {
    val joinedTime = it.getOrNull(RoomJoins.joinTime)
    val communityId = it.getOrNull(CommunityRooms.communityId)
    val room = Room.wrapRow(it)
    return room.toRoomInfo(joinedTime, communityId) to room.icon
}

suspend fun searchRoomInCommunity(communityId: OKey, uid: OKey?): Result<ServerResponse<RoomInfo>> {
    return runCatching {
        roomsResponse(DatabaseFactory.dbQuery {
            val join = Rooms.join(CommunityRooms, JoinType.INNER, Rooms.id, CommunityRooms.roomId)
                .join(Users, JoinType.INNER, Rooms.creator, Users.id)
            if (uid != null) {
                join
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(Rooms.fields + RoomJoins.joinTime)
                    .where {
                        CommunityRooms.communityId eq communityId and (RoomJoins.uid eq uid)
                    }
            } else {
                join
                    .select(Rooms.fields)
                    .where {
                        CommunityRooms.communityId eq communityId
                    }
            }.map {
                val joinedTime = it.getOrNull(RoomJoins.joinTime)
                val room = Room.wrapRow(it)
                room.toRoomInfo(joinedTime, communityId) to room.icon
            }

        })
    }
}

private fun Room.toRoomInfo(joinedTime: LocalDateTime?, communityId: OKey? = null) = RoomInfo(
    id,
    name,
    aid,
    creator,
    null,
    createdTime,
    joinedTime,
    communityId
)

suspend fun getRoom(roomId: OKey, uid: OKey?): Result<RoomInfo?> {
    return runCatching {
        DatabaseFactory.dbQuery {
            val baseJoin = Rooms.join(CommunityRooms, JoinType.LEFT, Rooms.id, CommunityRooms.roomId)
            val baseOp = Op.build {
                Rooms.id eq roomId
            }
            val baseFields = Rooms.fields + CommunityRooms.communityId
            if (uid != null) {
                baseJoin
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(baseFields + RoomJoins.joinTime)
                    .where {
                        baseOp and (RoomJoins.uid eq uid)
                    }
            } else {
                baseJoin
                    .select(baseFields)
                    .where {
                        baseOp
                    }
            }.map(::mapRoomInfo).firstOrNull()
        }?.let {
            val (info, iconName) = it
            val icon = backend.mediaService.get("apic", listOf(iconName)).firstOrNull()
            info.copy(icon = getMediaInfo(icon))
        }
    }
}

private fun roomsResponse(list: List<Pair<RoomInfo, String?>>): ServerResponse<RoomInfo> {
    val icons = backend.mediaService.get("apic", list.map {
        it.second
    })
    val data = list.mapIndexed { i, roomPair ->
        roomPair.first.copy(icon = getMediaInfo(icons[i]))
    }
    return ServerResponse(data, 10)
}
