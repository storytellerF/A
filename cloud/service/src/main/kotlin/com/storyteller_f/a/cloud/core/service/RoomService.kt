package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.CommonPath
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.backend.core.Backend
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
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.RoomDocument
import com.storyteller_f.a.backend.core.service.RoomDocumentSearch
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.toRoomInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.errorIfFalse
import com.storyteller_f.shared.utils.ifNotNull
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverIfDup
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
) = database.room.getRoomCommunityId(roomId).mapResult { communityId ->
    if (communityId == null) {
        // 检查是否存在title
        getJoinTitleByScope(uid, roomId)
    } else {
        database.container.isMemberJoined(communityId, uid).errorIfFalse {
            ForbiddenException("you should join community first.")
        }
    }.mapResult {
        database.container.getMember(roomId, uid).mapResult {
            if (it != null && it.status == MemberStatus.JOINED) {
                Result.success(it)
            } else {
                joinRoomOrUpdateMemberStatus(it, uid, roomId).onSuccess {
                    addUserLog(uid, UserLogType.JOIN, roomId ob ObjectType.ROOM)
                }
            }.mapResult {
                getRoomInfo(IdFetch(roomId), uid, true)
            }.recoverIfDup(database::isDup) {
                getRoomInfo(IdFetch(roomId), uid, true)
            }
        }
    }
}

private suspend fun Backend.joinRoomOrUpdateMemberStatus(
    member: Member?,
    uid: PrimaryKey,
    roomId: PrimaryKey,
): Result<Member> {
    val time = now()
    return if (member == null) {
        database.container.addMember(
            Member(
                SnowflakeFactory.nextId(),
                uid,
                roomId,
                ObjectType.ROOM,
                time,
                MemberStatus.JOINED,
                time
            )
        ).onSuccess {
            // 保存到 MemberSearchService
            getUserInfo(IdFetch(uid)).ifNotNull { userInfo ->
                memberSearchService.saveDocument(
                    listOf(
                        MemberDocument.fromUserInfo(it.id, userInfo, roomId, ObjectType.ROOM)
                    )
                ).onFailure { e ->
                    Napier.e(e) {
                        "save member document failed"
                    }
                }
            }
        }
    } else {
        database.container.updateMemberStatus(
            Member(
                member.id,
                member.uid,
                member.objectId,
                member.objectType,
                time,
                MemberStatus.JOINED,
                time
            )
        ).onSuccess {
            // 保存到 MemberSearchService
            getUserInfo(IdFetch(uid)).ifNotNull { userInfo ->
                memberSearchService.saveDocument(
                    listOf(
                        MemberDocument.fromUserInfo(it.id, userInfo, roomId, ObjectType.ROOM)
                    )
                ).onFailure { e ->
                    Napier.e(e) {
                        "save member document failed"
                    }
                }
            }
        }
    }
}

suspend fun Backend.getJoinTitleByScope(
    uid: PrimaryKey,
    scopeId: PrimaryKey
): Result<Unit> = database.title.getTitlePaginationResult(
    PrimaryKeyFetch(null, 1),
    uid,
    TitleSearchType.RECEIVER,
    TitleType.JOIN,
    scopeId
).mapResult {
    if (it.list.firstOrNull() != null) {
        UNIT_RESULT
    } else {
        Result.failure(ForbiddenException("Join failed."))
    }
}

suspend fun Backend.exitRoom(roomId: PrimaryKey, uid: PrimaryKey) =
    getRoomInfo(IdFetch(roomId), uid, true).mapResultIfNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else {
            database.container.deleteMember(roomId, uid).map {
                // 从 MemberSearchService 删除
                memberSearchService.deleteDocument(uid, roomId).onFailure { e ->
                    Napier.e(e) {
                        "delete member document failed"
                    }
                }
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
    return when (checkNickname(newRoom.name, 1..ROOM_NAME_LENGTH)) {
        StringCheckResult.RANGE_MISMATCH -> Result.failure(
            CustomBadRequestException("room name must be between in 1 and 20")
        )

        StringCheckResult.CONTAIN_INVALID_CHAR -> Result.failure(
            CustomBadRequestException("room name must be visible")
        )

        StringCheckResult.SUCCESS -> UNIT_RESULT
        else -> Result.failure(CustomBadRequestException("room name must be visible"))
    }
}

suspend fun Backend.createRoom(
    newRoom: NewRoom,
    uid: PrimaryKey,
): Result<RoomInfo?> {
    runCatching {
        checkAid(newRoom.aid).getOrThrow()
        checkRoomName(newRoom).getOrThrow()
    }.exceptionOrNull()?.let {
        return Result.failure(it)
    }
    val communityId = newRoom.communityId
    return if (communityId != null) {
        checkRootAdminPermission(ObjectType.COMMUNITY, communityId, uid).mapResultIfNotNull {
            UNIT_RESULT
        }
    } else {
        Result.success(Unit)
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
            // 保存创建者到 MemberSearchService
            getUserInfo(IdFetch(uid)).onSuccess { userInfo ->
                userInfo?.let {
                    memberSearchService.saveDocument(
                        listOf(
                            MemberDocument.fromUserInfo(memberId, it, room.id, ObjectType.ROOM)
                        )
                    ).onFailure { e ->
                        Napier.e(e) {
                            "save member document failed"
                        }
                    }
                }
            }
            room
        }
    }.mapResultIfNotNull { room ->
        processRawRoomToRoomInfo(listOf(RawRoom(room, room.createdTime)))
    }.mapIfNotNull(List<RoomInfo>::first)
}

suspend fun Backend.updateRoom(
    id: PrimaryKey,
    old: UpdateRoomBody,
    uid: PrimaryKey,
): Result<RoomInfo?> {
    val newUpdate = old.copy(name = old.name?.trim(), icon = old.icon)
    return checkRootAdminPermission(ObjectType.ROOM, id, uid).mapResultIfNotNull { permission ->
        runCatching {
            checkRoomName(newUpdate).getOrThrow()
            checkRoomIcon(newUpdate).getOrThrow()
        }
    }.mapResultIfNotNull {
        database.room.updateRoom(id, newUpdate)
    }.mapResultIfNotNull {
        getRoomInfo(IdFetch(id), uid, true)
    }
}

private suspend fun Backend.checkRoomIcon(update: UpdateRoomBody): Result<Unit> =
    checkIcon(update.icon, Dimension.ROOM_DIMENSION).mapResult { checkResult ->
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

private fun checkRoomName(update: UpdateRoomBody): Result<Unit> {
    return when (checkNickname(update.name, 1..ROOM_NAME_LENGTH)) {
        StringCheckResult.RANGE_MISMATCH -> Result.failure(
            CustomBadRequestException("room name must be between in 1 and $ROOM_NAME_LENGTH")
        )

        StringCheckResult.CONTAIN_INVALID_CHAR -> Result.failure(
            CustomBadRequestException("room name must be visible")
        )

        StringCheckResult.SUCCESS -> UNIT_RESULT
        else -> Result.failure(CustomBadRequestException("room name must be visible"))
    }
}

suspend fun searchRoomMembers(
    backend: Backend,
    p: CommonPath,
    uid: PrimaryKey?,
    q: CustomApi.Rooms.Id.Members.MemberQuery,
    f: PrimaryKeyFetch
) = backend.checkRootReadPermission(ObjectType.ROOM, p.id, uid).mapResultIfNotNull { permission ->
    if (permission.hasRead) {
        backend.searchContainerMembers(p.id, q.word, f)
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
        val keyword = RoomDocumentSearch.Keyword(listOf(word))
        roomSearchService.searchDocument(keyword, primaryKeyFetch).mapResult { (list, total) ->
            database.room.getRawRooms(
                ObjectListFetch.IdListFetch(list.map {
                    it.id
                }),
                uid
            ).map {
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

suspend fun Backend.getRoomInfoList(
    listFetch: ObjectListFetch.IdListFetch,
    uid: PrimaryKey? = null
) = database.room.getRawRooms(listFetch, uid).mapResult {
    processRawRoomToRoomInfo(it)
}

suspend fun Backend.getAllPublicRooms(primaryKeyFetch: PrimaryKeyFetch) =
    database.room.getRoomPaginationResult(
        uid = null,
        word = null,
        community = null,
        primaryKeyFetch = primaryKeyFetch,
        joinSearch = JoinSearch.Unspecified(null)
    ).mapResult { (list, total) ->
        processRawRoomToRoomInfo(list).map { PaginationResult(it, total) }
    }

suspend fun Backend.getAllPrivateRooms(primaryKeyFetch: PrimaryKeyFetch) =
    database.room.getPrivateRoomPaginationResult(
        primaryKeyFetch = primaryKeyFetch,
        word = null
    ).mapResult { (list, total) ->
        processRawRoomToRoomInfo(list).map { PaginationResult(it, total) }
    }

suspend fun Backend.getUserJoinedRooms(
    uid: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<RoomInfo>> =
    database.room.getRoomPaginationResult(
        uid = null,
        word = null,
        community = null,
        primaryKeyFetch = primaryKeyFetch,
        joinSearch = JoinSearch.Joined(uid)
    ).mapResult { (list, total) ->
        processRawRoomToRoomInfo(list).map { PaginationResult(it, total) }
    }

suspend fun Backend.getRoomMemberInfos(
    roomId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<MemberInfo>> = getContainerMemberInfos(roomId, primaryKeyFetch)
