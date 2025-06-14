package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.COMMUNITY_NAME_LENGTH
import com.storyteller_f.backend.service.CustomBadRequestException
import com.storyteller_f.backend.service.ForbiddenException
import com.storyteller_f.backend.service.ObjectFetch
import com.storyteller_f.backend.service.isDup
import com.storyteller_f.backend.service.processRoomRawResultToRoomInfo
import com.storyteller_f.backend.service.query.addRoomJoin
import com.storyteller_f.backend.service.query.createRoom
import com.storyteller_f.backend.service.query.exit
import com.storyteller_f.backend.service.query.getRoomPubKeyPaginationResult
import com.storyteller_f.backend.service.query.getRoomRawResult
import com.storyteller_f.backend.service.query.getTitlePaginationResult
import com.storyteller_f.backend.service.query.isMemberJoined
import com.storyteller_f.backend.service.query.updateRoom
import com.storyteller_f.backend.service.tables.Room
import com.storyteller_f.backend.service.tables.RoomRawResult
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
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

suspend fun Backend.getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
) = this.databaseSession.isMemberJoined(roomId, userId).mapResult {
    if (it) {
        this.databaseSession.getRoomPubKeyPaginationResult(roomId, primaryKeyFetch)
            .map { (data, count) ->
                PaginationResult(data, count)
            }
    } else {
        Result.failure(ForbiddenException("Permission denied"))
    }
}

suspend fun Backend.joinRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey
) = getRoomInfo(ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { roomInfo ->
    if (roomInfo.joinedTime != null) {
        Result.success(roomInfo)
    } else {
        val communityId = roomInfo.communityId
        if (communityId == null) {
            // 检查是否存在title
            this.databaseSession.getTitlePaginationResult(
                PrimaryKeyFetch(null, 1),
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
            this.databaseSession.isMemberJoined(communityId, uid).mapResult { hasJoined ->
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
    return this.databaseSession.addRoomJoin(
        roomInfo.id,
        uid,
        time,
    ).mapResult {
        addUserLog(uid, UserLogType.JOIN, roomInfo.tuple())
        Result.success(roomInfo.copy(joinedTime = time))
    }.recoverResult { exception ->
        if (exception.isDup()) {
            getRoomInfo(ObjectFetch.IdFetch(roomInfo.id), uid, true)
        } else {
            Result.failure(exception)
        }
    }
}

suspend fun Backend.exitRoom(roomId: PrimaryKey, uid: PrimaryKey) =
    getRoomInfo(ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else {
            this.databaseSession.exit(roomId, uid).map {
                addUserLog(uid, UserLogType.JOIN, roomId ob ObjectType.ROOM)
                info.copy(joinedTime = null)
            }
        }
    }

suspend fun Backend.getRoomInfo(
    objectFetch: ObjectFetch,
    uid: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<RoomInfo?> {
    return this.databaseSession.getRoomRawResult(objectFetch, fillJoinInfo, uid).mapResultIfNotNull {
        processRoomRawResultToRoomInfo(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
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
            databaseSession.createRoom(room)
                .mapResult {
                    processRoomRawResultToRoomInfo(
                        listOf(
                            RoomRawResult(room, room.icon, room.createdTime, null, 0)
                        )
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
                this.databaseSession.updateRoom(id, newRoom).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        this.databaseSession.getRoomRawResult(ObjectFetch.IdFetch(id), true, uid)
                            .mapResultIfNotNull {
                                processRoomRawResultToRoomInfo(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
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
