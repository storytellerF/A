package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CommonPath
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.core.NewRoom
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectFetch.IdFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.ROOM_NAME_LENGTH
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.toRoomInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.errorIfFalse
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier

suspend fun Backend.getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
) = database.container.isMemberJoined(roomId, userId).mapResult {
    if (it) {
        database.room.getRoomPubKeyPaginationResult(roomId, primaryKeyFetch)
    } else {
        Result.failure(ForbiddenException("Permission denied"))
    }
}

suspend fun Backend.joinRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey,
) = getRoomInfo(ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { roomInfo ->
    if (roomInfo.joinedTime != null) {
        Result.success(roomInfo)
    } else {
        val communityId = roomInfo.communityId
        if (communityId == null) {
            // 检查是否存在title
            database.title.getTitlePaginationResult(
                PrimaryKeyFetch(null, 1),
                uid,
                TitleSearchType.RECEIVER,
                TitleType.JOIN,
                roomId
            ).mapResult {
                if (it.list.firstOrNull() != null) {
                    UNIT_RESULT
                } else {
                    Result.failure(ForbiddenException("Join failed."))
                }
            }
        } else {
            database.container.isMemberJoined(communityId, uid).errorIfFalse {
                ForbiddenException("you should join community first.")
            }
        }
    }.mapResult {
        val time = now()
        val memberId = SnowflakeFactory.nextId()
        database.container.joinContainer(
            roomInfo.id,
            uid,
            time,
            ObjectType.ROOM,
            Member(memberId, uid, roomInfo.id, ObjectType.ROOM, time, MemberStatus.JOINED, time)
        ).mapResult {
            addUserLog(uid, UserLogType.JOIN, roomInfo.tuple())
            Result.success(roomInfo.copy(joinedTime = time))
        }.recoverResult { exception ->
            if (database.isDup(exception)) {
                getRoomInfo(IdFetch(roomInfo.id), uid, true)
            } else {
                Result.failure(exception)
            }
        }
    }
}

suspend fun Backend.exitRoom(roomId: PrimaryKey, uid: PrimaryKey) =
    getRoomInfo(ObjectFetch.IdFetch(roomId), uid, true).mapResultIfNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else {
            database.container.exitContainer(roomId, uid).map {
                addUserLog(uid, UserLogType.JOIN, roomId ob ObjectType.ROOM)
                info.copy(joinedTime = null)
            }
        }
    }

suspend fun Backend.getRoomInfo(
    objectFetch: ObjectFetch,
    uid: PrimaryKey?,
    fillJoinInfo: Boolean?,
) = database.room.getRawRoom(objectFetch, fillJoinInfo, uid).mapResultIfNotNull {
    processRawRoomToRoomInfo(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
}

private fun checkRoomName(newRoom: NewRoom): Result<Unit> {
    val nickname = newRoom.name
    return when {
        nickname.isEmpty() -> Result.failure(CustomBadRequestException("room name is empty"))
        nickname.length in 1..COMMUNITY_NAME_LENGTH -> UNIT_RESULT
        else -> Result.failure(CustomBadRequestException("room name must be between in 1 and 20"))
    }
}

suspend fun Backend.createRoom(
    newRoom: NewRoom,
    uid: PrimaryKey,
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
        checkRootAdminPermission(ObjectType.COMMUNITY, communityId, uid)
    } else {
        Result.success(true)
    }.mapResultIfNotNull {
        val roomId = SnowflakeFactory.nextId()
        val memberId = SnowflakeFactory.nextId()
        val room =
            Room(roomId, now(), newRoom.aid, newRoom.name, uid, newRoom.icon, communityId)
        database.room.createRoom(
            room,
            listOf(
                Member(
                    memberId,
                    room.creator,
                    room.id,
                    ObjectType.ROOM,
                    room.createdTime,
                    MemberStatus.JOINED,
                    room.createdTime
                )
            )
        ).map {
            roomSearchService.saveDocument(listOf(RoomDocument.fromRoom(room))).onFailure {
                Napier.e(it) {
                    "save room document failed"
                }
            }
            room
        }
    }.mapResultIfNotNull { room ->
        processRawRoomToRoomInfo(
            listOf(
                RawRoom(room, room.createdTime)
            )
        )
    }.mapIfNotNull(List<RoomInfo>::first)
}

suspend fun Backend.updateRoom(
    id: PrimaryKey,
    old: UpdateRoomBody,
    uid: PrimaryKey,
): Result<RoomInfo?> {
    val newRoom = old.copy(name = old.name?.trim(), icon = old.icon)
    return checkRootAdminPermission(ObjectType.ROOM, id, uid).mapResultIfNotNull { permission ->
        runCatching {
            checkRoomName(newRoom).getOrThrow()
            checkRoomIcon(newRoom).getOrThrow()
        }
    }.mapResultIfNotNull {
        database.room.updateRoom(id, newRoom)
    }.mapResultIfNotNull {
        getRoomInfo(ObjectFetch.IdFetch(id), uid, true)
    }
}

private suspend fun Backend.checkRoomIcon(newRoom: UpdateRoomBody): Result<Unit> =
    checkIcon(newRoom.icon, Dimension.ROOM_DIMENSION).mapResult { checkResult ->
        when (checkResult) {
            MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("icon not found"))
            MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                CustomBadRequestException("only support image")
            )

            MediaCheckResult.DIMENSION_MISMATCH -> Result.failure(
                CustomBadRequestException("dimension mismatch")
            )

            else -> UNIT_RESULT
        }
    }

private fun checkRoomName(newRoom: UpdateRoomBody): Result<Unit> =
    if (checkNickname(newRoom.name, 1..ROOM_NAME_LENGTH) == StringCheckResult.RANGE_MISMATCH) {
        Result.failure(CustomBadRequestException("community name must be between in 1 and 20"))
    } else {
        UNIT_RESULT
    }

suspend fun searchRoomMembers(
    backend: Backend,
    p: CommonPath,
    uid: PrimaryKey?,
    q: CustomApi.Rooms.Id.Members.MemberQuery,
    f: PrimaryKeyFetch
): Result<PaginationResult<UserInfo>?> =
    backend.checkRootReadPermission(ObjectType.ROOM, p.id, uid).mapResultIfNotNull { permission ->
        if (permission.hasRead) {
            backend.searchMembers(p.id, q.word, f)
        } else {
            Result.failure(UnauthorizedException())
        }
    }

suspend fun Backend.searchRoomPaginationResult(
    uid: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch,
    query: CustomApi.Rooms.RoomSearchQuery,
): Result<PaginationResult<RoomInfo>?> {
    val word = query.word
    val search = query.joinStatus.toJoinSearch(uid)
    val community = query.community
    return if (word.isNullOrBlank() || search !is JoinSearch.Unspecified) {
        database.room.getRoomPaginationResult(
            uid,
            word,
            community,
            primaryKeyFetch,
            search
        )
    } else {
        roomSearchService.searchDocument(RoomDocumentSearch.Keyword(listOf(word)), primaryKeyFetch)
            .mapResult { (list, total) ->
                database.room.getRawRooms(ObjectListFetch.IdListFetch(list.map {
                    it.id
                })).map {
                    PaginationResult(it, total)
                }
            }
    }.mapResult { (list, count) ->
        processRawRoomToRoomInfo(list).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}

suspend fun Backend.processRawRoomToRoomInfo(list: List<RawRoom>) =
    database.file.getFileRecordByIds(list.mapNotNull {
        it.room.icon
    }).mapResult { medias ->
        processFileRecordToFileInfo(medias).map { mediaList ->
            val mediaInfoMap = mediaList.associateBy { it.id }
            list.map { rawRoom ->
                rawRoom.toRoomInfo(icon = rawRoom.room.icon?.let { mediaInfoMap[it] })
            }
        }
    }

suspend fun Backend.getRoomInfoList(listFetch: ObjectListFetch.IdListFetch) =
    database.room.getRawRooms(listFetch).mapResult {
        processRawRoomToRoomInfo(it)
    }
