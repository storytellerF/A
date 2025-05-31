package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.*
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch

suspend fun Backend.getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    pagingFetch: PagingFetch
): Result<PaginationResult<Pair<PrimaryKey, String>>?> {
    return this.isMemberJoined(roomId, userId).mapResult {
        if (it) {
            commonPaginationRoomPubKeyList(roomId, pagingFetch)
                .map { (data, count) ->
                    PaginationResult(data, count)
                }
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }
}

suspend fun Backend.joinRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey
) = getRoom(ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { roomInfo ->
    if (roomInfo.joinedTime != null) {
        Result.success(roomInfo)
    } else {
        val communityId = roomInfo.communityId
        if (communityId == null) {
            // 检查是否存在title
            userTitles(
                PagingFetch(null, null, 1),
                uid,
                TitleSearchType.RECEIVER,
                TitleType.JOIN,
                roomId
            ).mapResult {
                if (it.list.firstOrNull() != null) {
                    directJoinRoom(uid, roomInfo)
                } else {
                    Result.failure(ForbiddenException("Join failed."))
                }
            }
        } else {
            this.isMemberJoined(communityId, uid).mapResult { hasJoined ->
                if (hasJoined) {
                    directJoinRoom(uid, roomInfo)
                } else {
                    Result.failure(ForbiddenException("you should join community first."))
                }
            }
        }
    }
}

private suspend fun Backend.directJoinRoom(
    uid: PrimaryKey,
    roomInfo: RoomInfo
): Result<RoomInfo?> {
    val time = now()
    return addRoomJoin(
        roomInfo.id,
        uid,
        time,
    ).mapResult {
        this.addUserLog(uid, UserLogType.JOIN, roomInfo.tuple())
        Result.success(roomInfo.copy(joinedTime = time))
    }.recoverError { exception ->
        if (exception.isDup()) {
            getRoom(ObjectFetch.IdFetch(roomInfo.id), uid, true)
        } else {
            Result.failure(exception)
        }
    }
}

suspend fun Backend.exitRoom(roomId: PrimaryKey, uid: PrimaryKey) =
    getRoom(ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else {
            exit(roomId, uid).map {
                this.addUserLog(uid, UserLogType.JOIN, roomId ob ObjectType.ROOM)
                info.copy(joinedTime = null)
            }
        }
    }

suspend fun Backend.getRoom(
    objectFetch: ObjectFetch,
    uid: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<RoomInfo?> {
    return getRoom(objectFetch, fillJoinInfo, uid).mapResultIfNotNull {
        this.processRoomList(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
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

suspend fun Backend.createRoom(
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
        checkRootAdminPermission(ObjectType.COMMUNITY, communityId, uid).mapIfNotNull {
            it.hasAdmin
        }
    } else {
        Result.success(true)
    }.mapResultIfNotNull {
        if (it) {
            val roomId = SnowflakeFactory.nextId()
            val room = Room(roomId, now(), newRoom.aid, newRoom.name, uid, newRoom.icon, communityId)
            createRoom(room)
                .mapResult {
                    this.processRoomList(
                        listOf(room.toRoomInfo(0, room.createdTime, null) to room.icon)
                    ).mapIfNotNull(List<RoomInfo>::first)
                }
        } else {
            Result.failure(ForbiddenException())
        }
    }
}

suspend fun Backend.updateRoom(
    id: PrimaryKey,
    old: UpdateRoomBody,
    uid: PrimaryKey
): Result<RoomInfo?> {
    val newRoom = old.copy(name = old.name?.trim(), icon = old.icon?.trim())
    return checkRootAdminPermission(ObjectType.ROOM, id, uid).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            val firstError = listOf(suspend {
                when (checkNickname(newRoom.name, 1..COMMUNITY_NAME_LENGTH)) {
                    StringCheckResult.RANGE_MISMATCH -> Result.failure(
                        CustomBadRequestException("community name must be between in 1 and 20")
                    )

                    else -> Result.success(Unit)
                }
            }, suspend {
                checkIcon(newRoom.icon, Dimension(1, 1)).mapResult { checkResult ->
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
                updateRoom(id, newRoom).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        getRoom(ObjectFetch.IdFetch(id), true, uid)
                            .mapResultIfNotNull {
                                this.processRoomList(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
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
