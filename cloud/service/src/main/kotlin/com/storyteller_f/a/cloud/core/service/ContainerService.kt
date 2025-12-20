package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch.IdFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.service.FileDocument
import com.storyteller_f.a.backend.core.types.FileRecord
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.UploadRecord
import com.storyteller_f.a.backend.core.types.toQuotaInfo
import com.storyteller_f.a.backend.core.types.toUploadRecordInfo
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
import com.storyteller_f.shared.utils.recoverIfDup
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

suspend fun Backend.checkTopicReadPermission(
    topicId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> =
    database.topic.getTopicRootTuple(topicId).mapResultIfNotNull { (rootId, rootType) ->
        checkRootReadPermission(rootType, rootId, uid)
    }

suspend fun Backend.checkRoomReadPermission(
    roomId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> =
    database.room.getRoomCommunityId(roomId).mapResult { communityId ->
        if (communityId == null && uid == null) {
            Result.failure(UnauthorizedException())
        } else {
            database.container.isMemberJoined(roomId, uid).map { hasJoined ->
                RootReadPermission(hasJoined || communityId != null, hasJoined, communityId == null)
            }
        }
    }

suspend fun Backend.checkCommunityReadPermission(
    communityId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> = database.community.getRawCommunity(IdFetch(communityId)).mapResultIfNotNull {
    database.container.isMemberJoined(communityId, uid).map { hasJoined ->
        RootReadPermission(true, hasJoined, false)
    }
}

suspend fun Backend.checkUserReadPermission(
    userId: PrimaryKey,
): Result<RootReadPermission?> = database.user.getRawUser(IdFetch(userId)).mapIfNotNull {
    RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false)
}

suspend fun Backend.checkRootReadPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey?,
): Result<RootReadPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> checkTopicReadPermission(parentId, uid)
        ObjectType.ROOM -> checkRoomReadPermission(parentId, uid)
        ObjectType.COMMUNITY -> checkCommunityReadPermission(parentId, uid)
        ObjectType.USER -> checkUserReadPermission(parentId)

        ObjectType.TITLE -> Result.success(RootReadPermission(hasRead = true, hasJoined = false, isPrivate = false))

        ObjectType.FILE -> Result.failure(ForbiddenException())
        ObjectType.PANEL_ACCOUNT -> Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkTopicWritePermission(
    topicId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> = database.getRawTopic(IdFetch(topicId), null).mapResultIfNotNull { topicInfo ->
    checkRootWritePermission(
        topicInfo.topic.rootType,
        topicInfo.topic.rootId,
        uid
    ).mapIfNotNull {
        it.copy(level = topicInfo.topic.level)
    }
}

suspend fun Backend.checkRoomWritePermission(
    roomId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> = database.room.getRawRoom(IdFetch(roomId), true, uid).mapResultIfNotNull {
    if (it.hasJoined) {
        Result.success(RootWritePermission(ObjectType.ROOM, roomId))
    } else {
        Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkCommunityWritePermission(
    communityId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> =
    database.community.getRawCommunity(IdFetch(communityId), true, uid).mapResultIfNotNull {
        if (it.hasJoined) {
            Result.success(RootWritePermission(ObjectType.COMMUNITY, communityId))
        } else {
            Result.failure(ForbiddenException())
        }
    }

suspend fun Backend.checkUserWritePermission(
    userId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> = if (uid == userId) {
    database.user.getRawUser(IdFetch(userId)).mapIfNotNull {
        RootWritePermission(ObjectType.USER, userId)
    }
} else {
    Result.failure(ForbiddenException())
}

suspend fun Backend.checkRootWritePermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootWritePermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> checkTopicWritePermission(parentId, uid)
        ObjectType.ROOM -> checkRoomWritePermission(parentId, uid)
        ObjectType.COMMUNITY -> checkCommunityWritePermission(parentId, uid)
        ObjectType.USER -> checkUserWritePermission(parentId, uid)

        ObjectType.TITLE -> Result.failure(ForbiddenException())
        ObjectType.FILE -> Result.failure(ForbiddenException())
        ObjectType.PANEL_ACCOUNT -> Result.failure(ForbiddenException())
    }
}

suspend fun Backend.checkTopicAdminPermission(
    topicId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> = database.topic.getTopicRootTuple(topicId)
    .mapResultIfNotNull { (rootId, rootType) ->
        checkRootAdminPermission(rootType, rootId, uid)
    }

suspend fun Backend.checkRoomAdminPermission(
    roomId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> = database.room.getRawRoom(IdFetch(roomId), true, uid)
    .mapResultIfNotNull {
        if (it.room.creator == uid) {
            Result.success(RootAdminPermission(ObjectType.ROOM, roomId))
        } else {
            Result.failure(ForbiddenException())
        }
    }

suspend fun Backend.checkCommunityAdminPermission(
    communityId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> = database.community.getRawCommunity(IdFetch(communityId))
    .mapResultIfNotNull {
        if (it.community.owner == uid) {
            Result.success(RootAdminPermission(ObjectType.COMMUNITY, communityId))
        } else {
            Result.failure(ForbiddenException())
        }
    }

suspend fun Backend.checkUserAdminPermission(
    userId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> = if (userId == uid) {
    database.user.getRawUser(IdFetch(userId)).mapIfNotNull {
        RootAdminPermission(ObjectType.USER, userId)
    }
} else {
    Result.failure(ForbiddenException())
}

suspend fun Backend.checkRootAdminPermission(
    parentType: ObjectType,
    parentId: PrimaryKey,
    uid: PrimaryKey,
): Result<RootAdminPermission?> {
    return when (parentType) {
        ObjectType.TOPIC -> checkTopicAdminPermission(parentId, uid)
        ObjectType.ROOM -> checkRoomAdminPermission(parentId, uid)
        ObjectType.COMMUNITY -> checkCommunityAdminPermission(parentId, uid)
        ObjectType.USER -> checkUserAdminPermission(parentId, uid)

        ObjectType.TITLE -> Result.success(RootAdminPermission(parentType, parentId))
        ObjectType.FILE -> Result.failure(ForbiddenException())
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
            }.onSuccess {
                // 保存文件文档到搜索服务
                fileSearchService.saveDocument(
                    t.map { fileRecord ->
                        FileDocument.fromFileRecord(fileRecord)
                    }
                ).onFailure { error ->
                    Napier.e(error) {
                        "save file document failed"
                    }
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
        val info = it.toQuotaInfo()
        val lockId = info.lockId
        if (lockId != null) {
            database.file.getUploadRecord(lockId).mapResult { rec ->
                Result.success(info.copy(extensions = QuotaInfo.Extensions(uploadRecord = rec?.toUploadRecordInfo())))
            }
        } else {
            Result.success(info)
        }
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
    val quota = Quota(ownerId, ownerType, 1024 * 1024 * 1024, 0, quotaType, null)
    return database.container.insertQuota(quota).map {
        quota
    }.recoverIfDup(database::isDup) {
        database.container.getQuotaInfo(ownerId, quotaType).mapResult {
            if (it == null) {
                Result.failure(Exception("get quota failed"))
            } else {
                Result.success(it)
            }
        }
    }.map {
        it.toQuotaInfo()
    }
}

val Backend.Companion.tika by lazy {
    Tika()
}
