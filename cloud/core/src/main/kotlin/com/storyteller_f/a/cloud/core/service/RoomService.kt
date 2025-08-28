package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CommonPath
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.RawRoom
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.toTopicInfo
import com.storyteller_f.a.backend.exposed.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.backend.exposed.isDup
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.index.TopicDocument
import com.storyteller_f.a.backend.service.processRawRoomToRoomInfo
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*

suspend fun Backend.getRoomPubKeys(
    roomId: PrimaryKey,
    userId: PrimaryKey,
    primaryKeyFetch: PrimaryKeyFetch,
) = combinedDatabase.containerDatabase.isMemberJoined(roomId, userId).mapResult {
    if (it) {
        combinedDatabase.roomData.getRoomPubKeyPaginationResult(roomId, primaryKeyFetch)
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
            combinedDatabase.containerDatabase.isMemberJoined(communityId, uid)
                .mapResult { hasJoined ->
                    if (hasJoined) {
                        UNIT_RESULT
                    } else {
                        Result.failure(ForbiddenException("you should join community first."))
                    }
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
): Result<RoomInfo?> {
    return combinedDatabase.roomData.getRawRoom(objectFetch, fillJoinInfo, uid).mapResultIfNotNull {
        processRawRoomToRoomInfo(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
    }
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
        checkRootAdminPermission(ObjectType.COMMUNITY, communityId, uid).mapIfNotNull {
            it.hasAdmin
        }
    } else {
        Result.success(true)
    }.mapResultIfNotNull {
        if (it) {
            val roomId = SnowflakeFactory.nextId()
            val room =
                Room(roomId, now(), newRoom.aid, newRoom.name, uid, newRoom.icon, communityId)
            combinedDatabase.roomData.createRoom(room)
                .mapResult {
                    processRawRoomToRoomInfo(
                        listOf(
                            RawRoom(room, room.createdTime, null, 0)
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
    uid: PrimaryKey,
): Result<RoomInfo?> {
    val newRoom = old.copy(name = old.name?.trim(), icon = old.icon)
    return checkRootAdminPermission(ObjectType.ROOM, id, uid).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            val firstError = listOf(suspend {
                when (checkNickname(newRoom.name, 1..COMMUNITY_NAME_LENGTH)) {
                    StringCheckResult.RANGE_MISMATCH -> Result.failure(
                        CustomBadRequestException("community name must be between in 1 and 20")
                    )

                    else -> UNIT_RESULT
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

                        else -> UNIT_RESULT
                    }
                }
            }).firstNotNullOfOrNull {
                it().exceptionOrNull()
            }
            if (firstError != null) {
                Result.failure(firstError)
            } else {
                combinedDatabase.roomData.updateRoom(id, newRoom).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        UNIT_RESULT
                    } else {
                        Result.success(null)
                    }
                }
            }
        } else {
            Result.failure(CustomBadRequestException("forbid"))
        }
    }.mapResult {
        combinedDatabase.roomData.getRawRoom(ObjectFetch.IdFetch(id), true, uid)
            .mapResultIfNotNull {
                processRawRoomToRoomInfo(listOf(it)).mapIfNotNull(List<RoomInfo>::first)
            }
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

suspend fun Backend.addTopicAtRoom(
    newTopic: NewRoomTopic,
    uid: PrimaryKey,
): Result<TopicInfo?> {
    return when (newTopic.parentType) {
        ObjectType.TOPIC -> {
            combinedDatabase.topicDatabase.getTopicRootTuple(newTopic.parentId)
                .mapResultIfNotNull { (id, type) ->
                    if (type == ObjectType.ROOM) {
                        Result.success(id)
                    } else {
                        Result.failure(ForbiddenException())
                    }
                }
        }

        ObjectType.ROOM -> {
            Result.success(newTopic.parentId)
        }

        else -> {
            Result.failure(ForbiddenException())
        }
    }.mapResultIfNotNull {
        addTopicIntoRoom(it, uid, newTopic)
    }
}

suspend fun Backend.addTopicIntoRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey,
    newTopic: NewRoomTopic,
): Result<TopicInfo?> {
    val bytes = when (val c = newTopic.content) {
        is TopicContent.Plain -> c.bytes
        is TopicContent.Encrypted -> c.bytes
        else -> throw CustomBadRequestException("unsupported type")
    }
    return combinedDatabase.containerDatabase.isMemberJoined(roomId, uid).mapResult { bool ->
        if (bool) {
            val content = newTopic.content
            val newId = SnowflakeFactory.nextId()
            combinedDatabase.roomData.checkRoomIsPrivate(roomId).mapResultIfNotNull { isPrivate ->
                val topic = Topic(
                    newId,
                    now(),
                    uid,
                    roomId,
                    ObjectType.ROOM,
                    newTopic.parentId,
                    newTopic.parentType,
                    bytes,
                    isPrivate,
                    false,
                    null
                )
                when {
                    isPrivate -> saveEncryptedTopic(content, roomId, topic)
                    content is TopicContent.Plain -> {
                        topicSearchService.saveDocument(
                            listOf(TopicDocument.fromTopic(topic, content))
                        ).getOrThrow()
                        combinedDatabase.topicDatabase.savePlainTopic(topic, content = content)
                            .map {
                                topic.toTopicInfo(content = content)
                            }
                    }

                    else -> Result.failure(ForbiddenException("Public room only accept unencrypted content."))
                }
            }.mapResultIfNotNull { topicInfo ->
                processTopicAfterCreate(topicInfo, uid)
            }
        } else {
            Result.failure(ForbiddenException("Can't publish content before join room."))
        }
    }
}

suspend fun Backend.saveEncryptedTopic(
    content: TopicContent,
    roomId: PrimaryKey,
    topic: Topic,
): Result<TopicInfo?> = if (content is TopicContent.Encrypted) {
    isKeyVerified(roomId, content.encryptedKey).mapResult {
        if (it) {
            combinedDatabase.topicDatabase.saveEncryptedTopic(topic, content)
        } else {
            Result.failure(ForbiddenException("Key not found ${content.encryptedKey.size}"))
        }
    }
} else {
    Result.failure(ForbiddenException("Private room only accept encrypted content."))
}

suspend fun Backend.searchRoomPaginationResult(
    uid: PrimaryKey?,
    word: String?,
    community: PrimaryKey?,
    primaryKeyFetch: PrimaryKeyFetch,
    search: JoinSearch,
): Result<PaginationResult<RoomInfo>?> {
    return combinedDatabase.roomData.getRoomPaginationResult(
        uid,
        word,
        community,
        primaryKeyFetch,
        search
    ).mapResult { (list, count) ->
        processRawRoomToRoomInfo(list).mapIfNotNull { value ->
            PaginationResult(value, count)
        }
    }
}
