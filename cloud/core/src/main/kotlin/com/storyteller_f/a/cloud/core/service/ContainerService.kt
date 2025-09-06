package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull

data class RootReadPermission(
    val hasRead: Boolean,
    val hasJoined: Boolean,
    val isPrivate: Boolean,
)

data class RootWritePermission(
    val rootType: ObjectType,
    val rootId: PrimaryKey,
    val level: Int = 0,
) {
    val tuple = ObjectTuple(rootId, rootType)
}

data class RootAdminPermission(
    val rootType: ObjectType,
    val rootId: PrimaryKey,
)

suspend fun Backend.checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            combinedDatabase.topicDatabase.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootReadPermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            combinedDatabase.roomDatabase.getRoomCommunityId(parentId).mapResult { communityId ->
                if (communityId == null && uid == null) {
                    Result.failure(UnauthorizedException())
                } else {
                    combinedDatabase.containerDatabase.isMemberJoined(parentId, uid)
                        .map { hasJoined ->
                            RootReadPermission(
                                hasJoined || communityId != null,
                                hasJoined,
                                communityId == null
                            )
                        }
                }
            }
        }

        ObjectType.COMMUNITY -> {
            combinedDatabase.communityDatabase.getRawCommunity(ObjectFetch.IdFetch(parentId))
                .mapResultIfNotNull {
                    combinedDatabase.containerDatabase.isMemberJoined(parentId, uid)
                        .map { hasJoined ->
                            RootReadPermission(true, hasJoined, false)
                        }
                }
        }

        ObjectType.USER -> combinedDatabase.userDatabase.getRawUser(ObjectFetch.IdFetch(parentId))
            .mapIfNotNull {
                RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
            }

        ObjectType.TITLE -> Result.success(
            RootReadPermission(
                hasRead = true,
                hasJoined = false,
                isPrivate = false
            )
        )

        ObjectType.File -> Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            combinedDatabase.topicDatabase.getTopicInfo(ObjectFetch.IdFetch(parentId), null)
                .mapResultIfNotNull { topicInfo ->
                    checkRootWritePermission(
                        topicInfo.rootType,
                        topicInfo.rootId,
                        uid
                    ).mapIfNotNull {
                        it.copy(level = topicInfo.level)
                    }
                }
        }

        ObjectType.ROOM -> {
            combinedDatabase.roomDatabase.getRoomCommunityId(parentId).mapResult {
                combinedDatabase.containerDatabase.isMemberJoined(parentId, uid)
                    .mapResult { hasJoined ->
                        if (hasJoined) {
                            Result.success(RootWritePermission(parentType, parentId))
                        } else {
                            Result.failure(ForbiddenException())
                        }
                    }
            }
        }

        ObjectType.COMMUNITY -> {
            combinedDatabase.communityDatabase.getRawCommunity(ObjectFetch.IdFetch(parentId))
                .mapResultIfNotNull {
                    combinedDatabase.containerDatabase.isMemberJoined(parentId, uid)
                        .mapResult { hasJoined ->
                            if (hasJoined) {
                                Result.success(RootWritePermission(parentType, parentId))
                            } else {
                                Result.failure(ForbiddenException())
                            }
                        }
                }
        }

        ObjectType.USER -> {
            if (uid == parentId) {
                combinedDatabase.userDatabase.getRawUser(ObjectFetch.IdFetch(parentId))
                    .mapIfNotNull {
                        RootWritePermission(parentType, parentId)
                    }
            } else {
                Result.failure(ForbiddenException())
            }
        }

        ObjectType.TITLE -> Result.failure(ForbiddenException())
        ObjectType.File -> Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkRootAdminPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            combinedDatabase.topicDatabase.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootAdminPermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            combinedDatabase.roomDatabase.getRawRoom(ObjectFetch.IdFetch(parentId), true, uid)
                .mapResultIfNotNull {
                    if (it.room.creator == uid) {
                        Result.success(
                            RootAdminPermission(
                                parentType,
                                parentId
                            )
                        )
                    } else {
                        Result.failure(ForbiddenException())
                    }
                }
        }

        ObjectType.COMMUNITY -> {
            combinedDatabase.communityDatabase.getRawCommunity(ObjectFetch.IdFetch(parentId))
                .mapResultIfNotNull {
                    if (it.community.owner == uid) {
                        Result.success(
                            RootAdminPermission(
                                parentType,
                                parentId
                            )
                        )
                    } else {
                        Result.failure(ForbiddenException())
                    }
                }
        }

        ObjectType.USER -> {
            if (parentId == uid) {
                combinedDatabase.userDatabase.getRawUser(ObjectFetch.IdFetch(parentId))
                    .mapIfNotNull {
                        RootAdminPermission(parentType, parentId)
                    }
            } else {
                Result.failure(ForbiddenException())
            }
        }

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId))
        ObjectType.File -> Result.failure(ForbiddenException())
    }
}
