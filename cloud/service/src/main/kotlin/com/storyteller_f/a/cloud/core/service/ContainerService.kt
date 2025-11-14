package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch.IdFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.core.types.toQuotaInfo
import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UploadRecordStatus
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier
import org.apache.tika.Tika

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
            database.topic.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootReadPermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            database.room.getRoomCommunityId(parentId).mapResult { communityId ->
                if (communityId == null && uid == null) {
                    Result.failure(UnauthorizedException())
                } else {
                    database.container.isMemberJoined(parentId, uid).map { hasJoined ->
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
            database.community.getRawCommunity(IdFetch(parentId)).mapResultIfNotNull {
                database.container.isMemberJoined(parentId, uid).map { hasJoined ->
                    RootReadPermission(true, hasJoined, false)
                }
            }
        }

        ObjectType.USER -> database.user.getRawUser(IdFetch(parentId)).mapIfNotNull {
            RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
        }

        ObjectType.TITLE -> Result.success(
            RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
        )

        ObjectType.File -> Result.failure(ForbiddenException())
        ObjectType.PANEL_ACCOUNT -> Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            database.topic.getRawTopic(IdFetch(parentId), null).mapResultIfNotNull { topicInfo ->
                checkRootWritePermission(
                    topicInfo.topic.rootType,
                    topicInfo.topic.rootId,
                    uid
                ).mapIfNotNull {
                    it.copy(level = topicInfo.topic.level)
                }
            }
        }

        ObjectType.ROOM -> {
            database.room.getRawRoom(IdFetch(parentId), true, uid).mapResultIfNotNull {
                val hasJoined = it.joinedTime != null
                if (hasJoined) {
                    Result.success(RootWritePermission(parentType, parentId))
                } else {
                    Result.failure(ForbiddenException())
                }
            }
        }

        ObjectType.COMMUNITY -> {
            database.community.getRawCommunity(IdFetch(parentId), true, uid).mapResultIfNotNull {
                val hasJoined = it.joinedTime != null
                if (hasJoined) {
                    Result.success(RootWritePermission(parentType, parentId))
                } else {
                    Result.failure(ForbiddenException())
                }
            }
        }

        ObjectType.USER -> {
            if (uid == parentId) {
                database.user.getRawUser(IdFetch(parentId)).mapIfNotNull {
                    RootWritePermission(parentType, parentId)
                }
            } else {
                Result.failure(ForbiddenException())
            }
        }

        ObjectType.TITLE -> Result.failure(ForbiddenException())
        ObjectType.File -> Result.failure(ForbiddenException())
        ObjectType.PANEL_ACCOUNT -> Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkRootAdminPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> {
            database.topic.getTopicRootTuple(parentId)
                .mapResultIfNotNull { (rootId, rootType) ->
                    checkRootAdminPermission(rootType, rootId, uid)
                }
        }

        ObjectType.ROOM -> {
            database.room.getRawRoom(IdFetch(parentId), true, uid)
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
            database.community.getRawCommunity(IdFetch(parentId))
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
                database.user.getRawUser(IdFetch(parentId))
                    .mapIfNotNull {
                        RootAdminPermission(parentType, parentId)
                    }
            } else {
                Result.failure(ForbiddenException())
            }
        }

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId))
        ObjectType.File -> Result.failure(ForbiddenException())
        ObjectType.PANEL_ACCOUNT -> Result.failure(ForbiddenException())
    }
}

/**
 * 执行出错会释放配额并标记为失败
 */
suspend fun Backend.lockQuotaInfo(
    objectTuple: ObjectTuple,
    quotaType: QuotaType,
    length: Long,
    name: String,
    block: suspend () -> Result<List<FileRecord>>
) = checkQuotaStatus(objectTuple, length, quotaType).mapResult { quotaInfo ->
    database.file.insertUploadRecord(
        UploadRecord(
            SnowflakeFactory.nextId(),
            now(),
            objectTuple.objectId,
            objectTuple.objectType,
            UploadRecordStatus.PENDING,
            length,
            0,
            name,
            0
        )
    ).mapResult { uploadRecord ->
        try {
            // 避免block 中出现未捕获的异常导致配额未释放
            val t = block().getOrThrow()
            val newRecord = uploadRecord.copy(status = UploadRecordStatus.SUCCESS)
            database.file.updateUploadRecordStatus(quotaInfo, newRecord, t).onFailure {
                Napier.e(it) {
                    "update upload record status failed ${uploadRecord.id} $quotaInfo $length"
                }
            }
            Result.success(t)
        } catch (e: Exception) {
            val record = uploadRecord.copy(status = UploadRecordStatus.FAILED)
            database.file.updateUploadRecordStatus(quotaInfo, record, emptyList()).onFailure {
                Napier.e(it) {
                    "update upload record status failed ${uploadRecord.id} $quotaInfo $length"
                }
            }
            Result.failure(e)
        }
    }
}

suspend fun Backend.getQuotaInfo(
    quotaType: QuotaType,
    objectTuple: ObjectTuple
) = database.container.getQuotaInfo(objectTuple.objectId, quotaType).mapResult {
    if (it == null) {
        insertQuotaAndGet(quotaType, objectTuple)
    } else {
        Result.success(it.toQuotaInfo())
    }
}

suspend fun Backend.checkQuotaStatus(
    tuple: ObjectTuple,
    size: Long,
    quotaType: QuotaType
): Result<QuotaInfo> = getQuotaInfo(quotaType, tuple).mapResult {
    if (it.lockId != null) {
        Result.failure(CustomBadRequestException("quota is locking"))
    } else if (it.used + size > it.total) {
        Result.failure(CustomBadRequestException("quota is not enough"))
    } else {
        Result.success(it)
    }
}

private suspend fun Backend.insertQuotaAndGet(
    quotaType: QuotaType,
    objectTuple: ObjectTuple
): Result<QuotaInfo> {
    val (ownerId, ownerType) = objectTuple
    val quota = Quota(
        ownerId,
        ownerType,
        1024 * 1024 * 1024,
        0,
        quotaType,
        null
    )
    return database.container.insertQuota(quota).map {
        quota
    }.recoverResult { throwable ->
        if (database.isDup(throwable)) {
            database.container.getQuotaInfo(ownerId, quotaType).mapResult {
                if (it == null) {
                    Result.failure(Exception("get quota failed"))
                } else {
                    Result.success(it)
                }
            }
        } else {
            Result.failure(throwable)
        }
    }.map {
        it.toQuotaInfo()
    }
}

val Backend.Companion.tika by lazy {
    Tika()
}
