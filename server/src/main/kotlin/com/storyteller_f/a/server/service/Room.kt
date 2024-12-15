package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.getMediaInfo
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverError
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.resources.*
import io.ktor.server.plugins.*

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
        class Members(val parent: Id, val word: String? = null)

        @Resource("join")
        class Join(val parent: Id)

        @Resource("pub-keys")
        class PubKeys(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id, val fillHasCommented: Boolean? = null)

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
            commonPaginationRoomPubKeyList(roomId, pre, next, size).map { (data, count) ->
                PaginationResult(data, count)
            }
        } else {
            Result.failure(ForbiddenException("Permission denied."))
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
        } else {
            exit(roomId, id).map { i ->
                info.copy(joinedTime = null)
            }
        }
    }

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
    return getRoomSource(roomId, roomAid, fillJoinInfo, uid).mapResultNotNull { (info, iconName) ->
        backend.mediaService.get("amedia", listOf(iconName)).map { value ->
            val icon = value.firstOrNull()
            info.copy(icon = getMediaInfo(icon))
        }
    }
}
