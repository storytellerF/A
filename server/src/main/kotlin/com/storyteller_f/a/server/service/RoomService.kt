package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.TitleSearchType
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch

suspend fun getRoomPubKeys(
    backend: Backend,
    roomId: PrimaryKey,
    userId: PrimaryKey,
    pagingFetch: PagingFetch
): Result<PaginationResult<Pair<PrimaryKey, String>>?> {
    return isMemberJoined(backend, roomId, userId).mapResult {
        if (it) {
            DatabaseFactory.commonPaginationRoomPubKeyList(backend, roomId, pagingFetch)
                .map { (data, count) ->
                    PaginationResult(data, count)
                }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }
}

suspend fun joinRoom(
    backend: Backend,
    roomId: PrimaryKey,
    uid: PrimaryKey
) = getRoom(backend, ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { roomInfo ->
    if (roomInfo.joinedTime != null) {
        Result.success(roomInfo)
    } else {
        val communityId = roomInfo.communityId
        if (communityId == null) {
            // 检查是否存在title
            DatabaseFactory.userTitles(
                backend,
                PagingFetch(null, null, 1),
                uid,
                TitleSearchType.RECEIVER,
                TitleType.JOIN,
                roomId
            ).mapResult {
                if (it.list.firstOrNull() != null) {
                    directJoinRoom(backend, uid, roomInfo)
                } else {
                    Result.failure(ForbiddenException("Join failed."))
                }
            }
        } else {
            isMemberJoined(backend, communityId, uid).mapResult { hasJoined ->
                if (hasJoined) {
                    directJoinRoom(backend, uid, roomInfo)
                } else {
                    Result.failure(ForbiddenException("you should join community first."))
                }
            }
        }
    }
}

private suspend fun directJoinRoom(
    backend: Backend,
    uid: PrimaryKey,
    roomInfo: RoomInfo
): Result<RoomInfo?> {
    val time = now()
    return DatabaseFactory.addRoomJoin(
        backend,
        roomInfo.id,
        uid,
        time,
        roomInfo.memberCount
    ).mapResult {
        addUserLog(backend, uid, UserLogType.JOIN, roomInfo.tuple())
        Result.success(roomInfo.copy(joinedTime = time))
    }.recoverError { exception ->
        if (exception.isDup()) {
            getRoom(backend, ObjectFetch.IdFetch(roomInfo.id), uid, true)
        } else {
            Result.failure(exception)
        }
    }
}

suspend fun exitRoom(backend: Backend, roomId: PrimaryKey, uid: PrimaryKey) =
    getRoom(backend, ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else {
            DatabaseFactory.exit(backend, roomId, uid).map {
                addUserLog(backend, uid, UserLogType.JOIN, roomId ob ObjectType.ROOM)
                info.copy(joinedTime = null)
            }
        }
    }

suspend fun getRoom(
    backend: Backend,
    objectFetch: ObjectFetch,
    uid: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<RoomInfo?> {
    return DatabaseFactory.getRoomSource(backend, objectFetch, fillJoinInfo, uid).mapResultIfNotNull {
        processRoomList(listOf(it), backend).mapIfNotNull(List<RoomInfo>::first)
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

suspend fun createRoom(
    backend: Backend,
    newRoom: NewRoom,
    uid: PrimaryKey
): Result<RoomInfo?> {
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
        checkRootAdminPermission(backend, ObjectType.COMMUNITY, communityId, uid).mapIfNotNull {
            it.hasAdmin
        }
    } else {
        Result.success(true)
    }.mapResultIfNotNull {
        if (it) {
            val roomId = SnowflakeFactory.nextId()
            val room = Room(roomId, now(), newRoom.aid, newRoom.name, uid, 0, newRoom.icon, communityId)
            DatabaseFactory.createRoom(backend, room)
                .mapResult {
                    processRoomList(
                        listOf(room.toRoomInfo(room.createdTime, null) to room.icon),
                        backend
                    ).mapIfNotNull(List<RoomInfo>::first)
                }
        } else {
            Result.failure(ForbiddenException())
        }
    }
}

suspend fun updateRoom(
    backend: Backend,
    id: PrimaryKey,
    old: UpdateRoomBody,
    uid: PrimaryKey
): Result<RoomInfo?> {
    val newRoom = old.copy(name = old.name?.trim(), icon = old.icon?.trim())
    return checkRootAdminPermission(backend, ObjectType.ROOM, id, uid).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            val firstError = listOf(suspend {
                when (checkNickname(newRoom.name, 1..COMMUNITY_NAME_LENGTH)) {
                    StringCheckResult.RANGE_MISMATCH -> Result.failure(
                        CustomBadRequestException("community name must be between in 1 and 20")
                    )

                    else -> Result.success(Unit)
                }
            }, suspend {
                checkIcon(backend, newRoom.icon, Dimension(1, 1)).mapResult { checkResult ->
                    when (checkResult) {
                        MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("icon not found"))
                        MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                            CustomBadRequestException("only support image")
                        )

                        MediaCheckResult.DIMENSION_MISMATCH -> Result.failure(
                            CustomBadRequestException("dimension mismatch")
                        )

                        else -> Result.success(Unit)
                    }
                }
            }).firstNotNullOfOrNull {
                it().exceptionOrNull()
            }
            if (firstError != null) {
                Result.failure(firstError)
            } else {
                DatabaseFactory.updateRoom(backend, id, newRoom).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        DatabaseFactory.getRoomSource(backend, ObjectFetch.IdFetch(id), true, uid)
                            .mapResultIfNotNull {
                                processRoomList(listOf(it), backend).mapIfNotNull(List<RoomInfo>::first)
                            }
                    } else {
                        Result.success(null)
                    }
                }
            }
        } else {
            Result.failure(CustomBadRequestException("forbid"))
        }
    }
}
