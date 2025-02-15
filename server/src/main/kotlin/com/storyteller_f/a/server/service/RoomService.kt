package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.COMMUNITY_NAME_LENGTH
import com.storyteller_f.CustomBadRequestException
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverError
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.*

suspend fun getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    pre: PrimaryKey?,
    next: PrimaryKey?,
    size: Int
): Result<PaginationResult<Pair<PrimaryKey, String>>?> {
    return isMemberJoined(roomId, userId).mapResult {
        if (it) {
            DatabaseFactory.commonPaginationRoomPubKeyList(roomId, pre, next, size).map { (data, count) ->
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
                    DatabaseFactory.addRoomJoin(roomId, uid, time).mapResult { affectedCount ->
                        Result.success(roomInfo.copy(joinedTime = time))
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
            DatabaseFactory.exit(roomId, id).map { i ->
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
    return DatabaseFactory.getRoomSource(roomId, roomAid, fillJoinInfo, uid).mapResultNotNull { (info, iconName) ->
        processRoomList(backend, iconName, info)
    }
}

private fun checkRoomName(newRoom: NewRoom): Result<Unit> {
    val nickname = newRoom.name
    return when {
        nickname.isEmpty() -> Result.failure(CustomBadRequestException("room name is empty"))
        nickname.length in 1..COMMUNITY_NAME_LENGTH -> Result.success(Unit)
        else -> Result.failure(CustomBadRequestException("room name must be between in 1 and 20"))
    }
}

suspend fun createRoom(newRoom: NewRoom, uid: PrimaryKey, backend: Backend): Result<RoomInfo?> {
    val firstError = listOf(suspend {
        checkAid(newRoom.aid)
    }, suspend {
        checkRoomName(newRoom)
    }).firstNotNullOfOrNull {
        it().exceptionOrNull()
    }
    if (firstError != null) return Result.failure(firstError)
    val communityId = newRoom.communityId
    return if (communityId != null) {
        checkRootAdminPermission(ObjectType.COMMUNITY, communityId, uid).mapNotNull {
            it.hasAdmin
        }
    } else {
        Result.success(true)
    }.mapResultNotNull {
        if (it) {
            val roomId = SnowflakeFactory.nextId()
            val room = Room(roomId, now(), newRoom.aid, newRoom.name, uid, newRoom.icon, communityId)
            DatabaseFactory.createRoom(room)
                .mapResult {
                    processRoomList(backend, room.icon, room.toRoomInfo(room.createdTime))
                }
        } else {
            Result.failure(ForbiddenException())
        }
    }
}
