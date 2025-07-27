package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.service.Backend
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
    val objectType: ObjectType,
    val objectId: PrimaryKey,
    val hasWrite: Boolean,
)

data class RootAdminPermission(
    val objectType: ObjectType,
    val objectId: PrimaryKey,
    val hasAdmin: Boolean,
)

suspend fun Backend.checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            exposedDatabase.topicDatabase.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootReadPermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            exposedDatabase.roomData.getRoomCommunityId(parentId).mapResult { communityId ->
                if (communityId == null && uid == null) {
                    Result.failure(UnauthorizedException())
                } else {
                    exposedDatabase.containerDatabase.isMemberJoined(parentId, uid)
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
            exposedDatabase.communityDatabase.checkCommunityExists(parentId).mapResultIfNotNull {
                exposedDatabase.containerDatabase.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> exposedDatabase.userDatabase.isUserExistsByUid(parentId).mapIfNotNull {
            RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
        }

        ObjectType.TITLE -> Result.success(
            RootReadPermission(
                hasRead = true,
                hasJoined = false,
                isPrivate = false
            )
        )

        ObjectType.MEDIA -> TODO()
    }
}

suspend fun Backend.checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            exposedDatabase.topicDatabase.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootWritePermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            exposedDatabase.roomData.getRoomCommunityId(parentId).mapResult {
                exposedDatabase.containerDatabase.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.COMMUNITY -> {
            exposedDatabase.communityDatabase.checkCommunityExists(parentId).mapResultIfNotNull {
                exposedDatabase.containerDatabase.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootWritePermission(parentType, parentId, hasJoined)
                }
            }
        }

        ObjectType.USER -> {
            if (uid == parentId) {
                exposedDatabase.userDatabase.isUserExistsByUid(parentId).mapIfNotNull {
                    RootWritePermission(parentType, parentId, parentId == uid)
                }
            } else {
                Result.failure(ForbiddenException("Permission denied"))
            }
        }

        ObjectType.TITLE -> Result.success(RootWritePermission(parentType, parentId, false))
        ObjectType.MEDIA -> TODO()
    }
}

suspend fun Backend.checkRootAdminPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            exposedDatabase.topicDatabase.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootAdminPermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            exposedDatabase.roomData.getRawRoom(ObjectFetch.IdFetch(parentId), true, uid)
                .mapIfNotNull {
                    RootAdminPermission(parentType, parentId, it.room.creator == uid)
                }
        }

        ObjectType.COMMUNITY -> {
            exposedDatabase.communityDatabase.getRawCommunity(ObjectFetch.IdFetch(parentId))
                .mapIfNotNull {
                    RootAdminPermission(parentType, parentId, it.community.owner == uid)
                }
        }

        ObjectType.USER -> {
            exposedDatabase.userDatabase.isUserExistsByUid(parentId).mapIfNotNull {
                RootAdminPermission(parentType, parentId, parentId == uid)
            }
        }

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId, false))
        ObjectType.MEDIA -> TODO()
    }
}
