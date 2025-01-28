package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
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
        backend.mediaService.get(AMEDIA_BUCKET, listOf(iconName)).map { value ->
            val icon = value.firstOrNull()
            info.copy(icon = icon)
        }
    }
}
