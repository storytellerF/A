package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.UnauthorizedException
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverError
import com.storyteller_f.tables.*
import com.storyteller_f.tables.Rooms
import com.storyteller_f.types.PaginationResult
import io.ktor.resources.Resource
import io.ktor.server.plugins.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

@Resource("/rooms")
class RouteRooms(val aid: String? = null, val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteRooms = RouteRooms(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null,
        val community: PrimaryKey? = null,
    )

    @Resource("{id}")
    class Id(val parent: RouteRooms = RouteRooms(), val id: PrimaryKey) {
        @Resource("members")
        class Members(val parent: Id, val word: String?)

        @Resource("join")
        class Join(val parent: Id)

        @Resource("pub-keys")
        class PubKeys(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id)

        @Resource("exit")
        class Exit(val parent: Id)
    }
}

suspend fun getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    pre: PrimaryKey?,
    next: PrimaryKey?,
    size: Int
): Result<PaginationResult<Pair<PrimaryKey, String>>?> {
    return isMemberJoined(roomId, userId).mapResult {
        if (it) {
            DatabaseFactory.mapQuery({
                this[Users.id] to this[Users.publicKey]
            }) {
                buildRoomPubKeyQuery(roomId, false).bindPaginationQuery(Users, pre, next, size)
            }.mapResult { data ->
                DatabaseFactory.count {
                    buildRoomPubKeyQuery(roomId, true)
                }.map { count ->
                    PaginationResult(data, count)
                }
            }
        } else {
            Result.failure(ForbiddenException("Permission denied."))
        }
    }
}

private fun buildRoomPubKeyQuery(roomId: PrimaryKey, getCount: Boolean): Query {
    val join = Users.join(MemberJoins, JoinType.INNER, Users.id, MemberJoins.uid)
    return if (getCount) {
        join
            .selectAll()
            .where {
                MemberJoins.objectId eq roomId
            }
    } else {
        join
            .select(Users.id, Users.publicKey)
            .where {
                MemberJoins.objectId eq roomId
            }
    }
}

suspend fun joinRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey,
    backend: Backend
) = getRoom(roomId, null, uid, backend, true).mapResultNotNull { roomInfo ->
    if (roomInfo.joinedTime != null) {
        Result.success(roomInfo)
    } else {
        val communityId = roomInfo.communityId
        if (communityId == null) {
            Result.failure(ForbiddenException("Join failed."))
        } else {
            isMemberJoined(communityId, uid).mapResult { hasJoined ->
                if (hasJoined) {
                    val time = now()
                    addRoomJoin(roomId, uid, time).mapResult { affectedCount ->
                        if (affectedCount > 0) {
                            Result.success(roomInfo.copy(joinedTime = time))
                        } else {
                            Result.failure(ForbiddenException("Join failed."))
                        }
                    }.recoverError { exception ->
                        if (exception.isDup()) {
                            getRoom(roomId, null, uid, backend, true)
                        } else {
                            Result.failure(exception)
                        }
                    }
                } else {
                    Result.failure(ForbiddenException("you should join community first."))
                }
            }
        }
    }

}

suspend fun exitRoom(roomId: PrimaryKey, id: PrimaryKey, backend: Backend) =
    getRoom(roomId, null, id, backend, true).mapResultNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else
            exit(roomId, id).map { i ->
                info.copy(joinedTime = null)
            }
    }

fun mapRoomInfo(it: ResultRow): Pair<RoomInfo, String?> {
    val joinedTime = it.getOrNull(MemberJoins.joinTime)
    val room = Room.wrapRow(it)
    return room.toRoomInfo(joinedTime) to room.icon
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

suspend fun getRoom(
    roomId: PrimaryKey?,
    roomAid: String?,
    uid: PrimaryKey?,
    backend: Backend,
    fillJoinInfo: Boolean?
): Result<RoomInfo?> {
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

        when {
            fillJoinInfo != true -> Rooms
                .select(Rooms.fields)
                .where {
                    baseOp and (Rooms.communityId.isNotNull())
                }

            uid != null -> Rooms
                .join(MemberJoins, JoinType.LEFT, Rooms.id, MemberJoins.objectId) {
                    MemberJoins.uid eq uid
                }
                .select(Rooms.fields + MemberJoins.joinTime)
                .where {
                    baseOp
                }

            else -> throw UnauthorizedException()
        }
    }.mapResultNotNull { (info, iconName) ->
        backend.mediaService.get("apic", listOf(iconName)).map { value ->
            val icon = value.firstOrNull()
            info.copy(icon = getMediaInfo(icon))
        }
    }
}
