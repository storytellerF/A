package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.tables.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*


suspend fun getRoomPubKeys(
    roomId: OKey,
    userId: OKey,
    pre: OKey?,
    next: OKey?,
    size: Int
) = if (isRoomJoined(roomId, userId)) {
    runCatching {
        val data = DatabaseFactory.dbQuery {
            val query = Users.join(RoomJoins, JoinType.INNER, Users.id, RoomJoins.uid)
                .select(Users.id, Users.publicKey)
                .where {
                    RoomJoins.roomId eq roomId
                }
            if (next != null) {
                query.andWhere {
                    Users.id less next
                }
            } else if (pre != null) {
                query.andWhere {
                    Users.id greater pre
                }
            }
            query
                .limit(size)
                .orderBy(Users.id, SortOrder.DESC)
                .map {
                    it[Users.id] to it[Users.publicKey]
                }
        }
        val count = DatabaseFactory.count {
            Users.join(RoomJoins, JoinType.INNER, Users.id, RoomJoins.uid).selectAll().where {
                RoomJoins.roomId eq roomId
            }
        }
        data to count
    }
} else {
    Result.failure(ForbiddenException("Permission denied."))
}


suspend fun joinRoom(
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


suspend fun searchRooms(
    word: String,
    uid: OKey?,
    backend: Backend,
    preRoomId: OKey?,
    nextRoomId: OKey?,
    size: Int
): Result<Pair<List<RoomInfo>, Long>> {
    return runCatching {
        val r = DatabaseFactory.mapQuery(::mapRoomInfo) {
            val baseJoin = Rooms.join(CommunityRooms, JoinType.INNER, Rooms.id, CommunityRooms.roomId)
            val baseOp = Op.build {
                Rooms.name like "%$word%"
            }
            val baseFields = Rooms.fields + CommunityRooms.communityId
            val query = if (uid != null) {
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
            }
            query.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
        }
        val count = DatabaseFactory.count {
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
            }
        }
        roomsResponse(r, backend) to count
    }
}

suspend fun searchJoinedRooms(
    uid: OKey,
    backend: Backend,
    preRoomId: OKey?,
    nextRoomId: OKey?,
    size: Int
): Result<Pair<List<RoomInfo>, Long>> {
    return runCatching {
        val list = DatabaseFactory.mapQuery(::mapRoomInfo) {
            RoomJoins.join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
                .join(CommunityRooms, JoinType.LEFT, RoomJoins.roomId, CommunityRooms.roomId)
                .select(Rooms.fields + RoomJoins.joinTime + CommunityRooms.communityId)
                .where {
                    RoomJoins.uid eq uid
                }.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
        }
        val count = DatabaseFactory.count {
            RoomJoins.join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
                .join(CommunityRooms, JoinType.LEFT, RoomJoins.roomId, CommunityRooms.roomId)
                .selectAll()
                .where {
                    RoomJoins.uid eq uid
                }
        }
        roomsResponse(list, backend) to count
    }
}

fun mapRoomInfo(it: ResultRow): Pair<RoomInfo, String?> {
    val joinedTime = it.getOrNull(RoomJoins.joinTime)
    val communityId = it.getOrNull(CommunityRooms.communityId)
    val room = Room.wrapRow(it)
    return room.toRoomInfo(joinedTime, communityId) to room.icon
}

suspend fun searchRoomInCommunity(
    communityId: OKey,
    uid: OKey?,
    backend: Backend,
    preRoomId: OKey?,
    nextRoomId: OKey?,
    size: Int
): Result<Pair<List<RoomInfo>, Long>> {
    return runCatching {
        val list = DatabaseFactory.mapQuery({
            val joinedTime = it.getOrNull(RoomJoins.joinTime)
            val room = Room.wrapRow(it)
            room.toRoomInfo(joinedTime, communityId) to room.icon
        }) {
            val join = Rooms.join(CommunityRooms, JoinType.INNER, Rooms.id, CommunityRooms.roomId)
                .join(Users, JoinType.INNER, Rooms.creator, Users.id)
            val query = if (uid != null) {
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
            }
            query.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
        }
        val count = DatabaseFactory.count {
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
            }

        }
        roomsResponse(list, backend = backend) to count
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

suspend fun getRoom(roomId: OKey, uid: OKey?, backend: Backend): Result<RoomInfo?> {
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

private fun roomsResponse(list: List<Pair<RoomInfo, String?>>, backend: Backend): List<RoomInfo> {
    val icons = backend.mediaService.get("apic", list.map {
        it.second
    })
    return list.mapIndexed { i, roomPair ->
        roomPair.first.copy(icon = getMediaInfo(icons[i]))
    }
}
