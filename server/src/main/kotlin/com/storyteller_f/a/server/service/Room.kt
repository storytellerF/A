package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.tables.Rooms
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

suspend fun getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    pre: PrimaryKey?,
    next: PrimaryKey?,
    size: Int
): Result<PaginationResult<Pair<PrimaryKey, String>>?> {
    return isRoomJoined(roomId, userId).mapResult {
        if (it) {
            val query = Users.join(RoomJoins, JoinType.INNER, Users.id, RoomJoins.uid)
                .select(Users.id, Users.publicKey)
                .where {
                    RoomJoins.roomId eq roomId
                }
            DatabaseFactory.mapQuery({
                this[Users.id] to this[Users.publicKey]
            }) {
                query.bindPaginationQuery(Users, pre, next, size)
            }.mapResult { data ->
                DatabaseFactory.count {
                    query
                }.map { count ->
                    PaginationResult(data, count)
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied."))
        }
    }
}

suspend fun joinRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey
) = DatabaseFactory.queryNotNull({
    toRoomInfo(joinedTime = null)
}, Room::wrapRow) {
    Room.findRoomById(roomId)
}.mapResultNotNull { roomInfo ->
    val communityId = roomInfo.communityId
    if (communityId == null) {
        Result.failure(ForbiddenException("Join failed."))
    } else {
        isCommunityJoined(communityId, uid).mapResult { hasJoined ->
            if (hasJoined) {
                DatabaseFactory.insert {
                    addRoomJoin(roomId, uid)
                }.mapResult { affectedCount ->
                    if (affectedCount > 0) {
                        Result.success(roomInfo.copy(joinedTime = now()))
                    } else {
                        Result.failure(ForbiddenException("Join failed."))
                    }
                }
            } else {
                Result.failure(ForbiddenException("you should join community first."))
            }
        }
    }
}

suspend fun searchRooms(
    word: String,
    uid: PrimaryKey?,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int
): Result<PaginationResult<RoomInfo>?> {
    val baseOp = Op.build {
        Rooms.name like "%$word%"
    }
    val baseFields = Rooms.fields
    val baseQuery = if (uid != null) {
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
    return DatabaseFactory.mapQuery(::mapRoomInfo) {
        baseQuery.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
    }.mapResult { r ->
        DatabaseFactory.count {
            baseQuery
        }.mapResult { count ->
            roomsResponse(r, backend).map { value ->
                PaginationResult(value, count)
            }
        }
    }
}

suspend fun searchJoinedRooms(
    uid: PrimaryKey,
    backend: Backend,
    preRoomId: PrimaryKey?,
    nextRoomId: PrimaryKey?,
    size: Int
): Result<PaginationResult<RoomInfo>?> {
    val baseQuery = RoomJoins.join(Rooms, JoinType.INNER, RoomJoins.roomId, Rooms.id)
        .select(Rooms.fields + RoomJoins.joinTime)
        .where {
            RoomJoins.uid eq uid
        }
    return DatabaseFactory.mapQuery(::mapRoomInfo) {
        baseQuery.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
    }.mapResult { list ->
        DatabaseFactory.count {
            baseQuery
        }.mapResult { count ->
            roomsResponse(list, backend).map { value ->
                PaginationResult(value, count)
            }
        }
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
): Result<PaginationResult<RoomInfo>?> {
    val join = Rooms
        .join(Users, JoinType.INNER, Rooms.creator, Users.id)
    val baseQuery = if (uid != null) {
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
    return DatabaseFactory.mapQuery({
        val joinedTime = getOrNull(RoomJoins.joinTime)
        val room = Room.wrapRow(this)
        room.toRoomInfo(joinedTime) to room.icon
    }) {
        baseQuery.bindPaginationQuery(Rooms, preRoomId, nextRoomId, size)
    }.mapResult { list ->
        DatabaseFactory.count {
            baseQuery
        }.mapResult { count ->
            roomsResponse(list, backend = backend).map { value ->
                PaginationResult(value, count)
            }
        }
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
    return DatabaseFactory.first({
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
    }.mapResultNotNull { (info, iconName) ->
        backend.mediaService.get("apic", listOf(iconName)).map { value ->
            val icon = value.firstOrNull()
            info.copy(icon = getMediaInfo(icon))
        }
    }
}

private fun roomsResponse(list: List<Pair<RoomInfo, String?>>, backend: Backend): Result<List<RoomInfo>> {
    return backend.mediaService.get("apic", list.map {
        it.second
    }).map { icons ->
        list.mapIndexed { i, roomPair ->
            roomPair.first.copy(icon = getMediaInfo(icons[i]))
        }
    }
}
