package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.*
import com.storyteller_f.tables.Rooms
import io.ktor.server.plugins.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

suspend fun getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    pre: PrimaryKey?,
    next: PrimaryKey?,
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
    room: PrimaryKey,
    id: PrimaryKey
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
    uid: PrimaryKey?,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int
): Result<Pair<List<RoomInfo>, Long>> {
    return runCatching {
        val r = DatabaseFactory.mapQuery(::mapRoomInfo) {
            val baseOp = Op.build {
                Rooms.name like "%$word%"
            }
            val baseFields = Rooms.fields
            val query = if (uid != null) {
                Rooms
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(baseFields + RoomJoins.joinTime)
                    .where {
                        baseOp and (RoomJoins.uid eq uid)
                    }
            } else {
                Rooms
                    .select(baseFields)
                    .where {
                        baseOp and Rooms.communityId.isNotNull()
                    }
            }
            query.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
        }
        val count = DatabaseFactory.count {
            val baseOp = Op.build {
                Rooms.name like "%$word%"
            }
            val baseFields = Rooms.fields
            if (uid != null) {
                Rooms
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(baseFields + RoomJoins.joinTime)
                    .where {
                        baseOp and (RoomJoins.uid eq uid)
                    }
            } else {
                Rooms
                    .select(baseFields)
                    .where {
                        baseOp and Rooms.communityId.isNotNull()
                    }
            }
        }
        roomsResponse(r, backend) to count
    }
}

suspend fun searchJoinedRooms(
    uid: PrimaryKey,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int
): Result<Pair<List<RoomInfo>, Long>> {
    return runCatching {
        val list = DatabaseFactory.mapQuery(::mapRoomInfo) {
            RoomJoins.join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
                .select(Rooms.fields + RoomJoins.joinTime)
                .where {
                    RoomJoins.uid eq uid
                }.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
        }
        val count = DatabaseFactory.count {
            RoomJoins.join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
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
    val room = Room.wrapRow(it)
    return room.toRoomInfo(joinedTime) to room.icon
}

suspend fun searchRoomInCommunity(
    communityId: PrimaryKey,
    uid: PrimaryKey?,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int
): Result<Pair<List<RoomInfo>, Long>> {
    return runCatching {
        val list = DatabaseFactory.mapQuery({
            val joinedTime = getOrNull(RoomJoins.joinTime)
            val room = Room.wrapRow(this)
            room.toRoomInfo(joinedTime) to room.icon
        }) {
            val join = Rooms
                .join(Users, JoinType.INNER, Rooms.creator, Users.id)
            val query = if (uid != null) {
                join
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(Rooms.fields + RoomJoins.joinTime)
                    .where {
                        RoomJoins.uid eq uid and (Rooms.communityId eq communityId)
                    }
            } else {
                join
                    .select(Rooms.fields)
                    .where {
                        Rooms.communityId eq communityId
                    }
            }
            query.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
        }
        val count = DatabaseFactory.count {
            val join = Rooms
                .join(Users, JoinType.INNER, Rooms.creator, Users.id)
            if (uid != null) {
                join
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(Rooms.fields + RoomJoins.joinTime)
                    .where {
                        (RoomJoins.uid eq uid) and (Rooms.communityId eq communityId)
                    }
            } else {
                join
                    .select(Rooms.fields)
                    .where {
                        Rooms.communityId eq communityId
                    }
            }
        }
        roomsResponse(list, backend = backend) to count
    }
}

private fun Room.toRoomInfo(joinedTime: LocalDateTime?) = RoomInfo(
    id,
    name,
    aid,
    creator,
    null,
    createdTime,
    joinedTime,
    communityId
)

suspend fun getRoom(roomId: PrimaryKey?, roomAid: String?, uid: PrimaryKey?, backend: Backend): Result<RoomInfo?> {
    if (roomId == null && roomAid == null) {
        return Result.failure(BadRequestException("roomId or roomAid must be set."))
    }
    return runCatching {
        DatabaseFactory.first({
            this
        }, ::mapRoomInfo) {
            val baseOp = Op.build {
                if (roomId != null) {
                    Rooms.id eq roomId
                } else {
                    Rooms.aid eq roomAid!!
                }
            }
            val baseFields = Rooms.fields
            if (uid != null) {
                // 检查用户是否加入，查询加入时间
                Rooms
                    .join(RoomJoins, JoinType.INNER, Rooms.id, RoomJoins.roomId)
                    .select(baseFields + RoomJoins.joinTime)
                    .where {
                        baseOp and (RoomJoins.uid eq uid)
                    }
            } else {
                Rooms
                    .select(baseFields)
                    .where {
                        // 未登录，只能查找社区的聊天室
                        baseOp and (Rooms.communityId.isNotNull())
                    }
            }
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
