package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CommonPath
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.toRoomInfo
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import io.github.aakira.napier.Napier

suspend fun Backend.getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
) = combinedDatabase.containerDatabase.isMemberJoined(roomId, userId).mapResult {
    if (it) {
        combinedDatabase.roomDatabase.getRoomPubKeyPaginationResult(roomId, primaryKeyFetch)
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
            combinedDatabase.titleDatabase.getTitlePaginationResult(
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
            combinedDatabase.containerDatabase.isMemberJoined(communityId, uid).errorIfFalse {
                ForbiddenException("you should join community first.")
            }
        }
    }.mapResult {
        directJoinRoom(uid, roomInfo)
    }
}

private suspend fun Backend.directJoinRoom(
    uid: PrimaryKey,
    roomInfo: RoomInfo,
): Result<RoomInfo?> {
    val time = now()
    return combinedDatabase.containerDatabase.joinContainer(
        roomInfo.id,
        uid,
        time,
        ObjectType.ROOM,
    ).mapResult {
        addUserLog(uid, UserLogType.JOIN, roomInfo.tuple())
        Result.success(roomInfo.copy(joinedTime = time))
    }.recoverResult { exception ->
        if (combinedDatabase.isDup(exception)) {
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
            combinedDatabase.containerDatabase.exitContainer(roomId, uid).map {
                addUserLog(uid, UserLogType.JOIN, roomId ob ObjectType.ROOM)
                info.copy(joinedTime = null)
            }
        }
    }

suspend fun Backend.getRoomInfo(
    objectFetch: ObjectFetch,
    uid: PrimaryKey?,
    fillJoinInfo: Boolean?,
) = combinedDatabase.roomDatabase.getRawRoom(objectFetch, fillJoinInfo, uid).mapResultIfNotNull {
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
        val room =
            Room(roomId, now(), newRoom.aid, newRoom.name, uid, newRoom.icon, communityId)
        combinedDatabase.roomDatabase.createRoom(room).map {
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
            when (checkNickname(newRoom.name, 1..COMMUNITY_NAME_LENGTH)) {
                StringCheckResult.RANGE_MISMATCH -> Result.failure(
                    CustomBadRequestException("community name must be between in 1 and 20")
                )

                else -> UNIT_RESULT
            }.getOrThrow()
            checkIcon(newRoom.icon, Dimension(1, 1)).mapResult { checkResult ->
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
            }.getOrThrow()
        }
    }.mapResultIfNotNull {
        combinedDatabase.roomDatabase.updateRoom(id, newRoom)
    }.mapResultIfNotNull {
        getRoomInfo(ObjectFetch.IdFetch(id), uid, true)
    }
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
        combinedDatabase.roomDatabase.getRoomPaginationResult(
            uid,
            word,
            community,
            primaryKeyFetch,
            search
        )
    } else {
        roomSearchService.searchDocument(RoomDocumentSearch.Keyword(listOf(word)), primaryKeyFetch)
            .mapResult { (list, total) ->
                combinedDatabase.roomDatabase.getRawRooms(ObjectListFetch.IdListFetch(list.map {
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
    combinedDatabase.fileDatabase.getFileRecordByIds(list.mapNotNull {
        it.room.icon
    }).mapResult { medias ->
        processFileRecordToFileInfo(medias).map { mediaList ->
            val mediaInfoMap = mediaList.associateBy { it.id }
            list.map { rawRoom ->
                rawRoom.room.toRoomInfo()
                    .copy(
                        icon = rawRoom.room.icon?.let { mediaInfoMap[it] },
                        joinedTime = rawRoom.joinedTime,
                        lastRead = rawRoom.lastRead,
                        memberCount = rawRoom.memberCount ?: 0,
                        latestTopic = rawRoom.latestTopic
                    )
            }
        }
    }

suspend fun getCommunityRoomsTemplateList(community: Community): List<Room> {
    val communityAid = community.aid
    return listOf(
        "${communityAid}_general" to "General",
        "${communityAid}_lobby" to "Lobby",
        "${communityAid}_support" to "Support"
    ).mapIndexed { i, pair ->
        Room(
            SnowflakeFactory.nextId(),
            now(),
            pair.first,
            pair.second,
            community.owner,
            communityId = community.id
        )
    }
}

suspend fun Backend.getRoomInfoList(listFetch: ObjectListFetch.IdListFetch): Result<List<RoomInfo>> =
    combinedDatabase.roomDatabase.getRawRooms(listFetch).mapResult {
        processRawRoomToRoomInfo(it)
    }
