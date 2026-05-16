package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewUser
import com.storyteller_f.a.api.TransferAuthKey
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.PanelLog
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toPanelLogInfo
import com.storyteller_f.a.backend.core.types.toTaskRecordInfo
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.PanelLogInfo
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.TaskRecordInfo
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.UpdateObjectStatusBody
import com.storyteller_f.shared.obj.UpdateUserStatusBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now

suspend fun Backend.getOverview() = runCatching {
    val userCount = database.user.getUserCount().getOrThrow()
    val topicCount = database.topic.getTopicCount().getOrThrow()
    val communityCount = database.community.getCommunityCount().getOrThrow()
    val privateRoomCount = database.room.getPrivateRoomCount().getOrThrow()
    val publicRoomCount = database.room.getPublicRoomCount().getOrThrow()
    val fileCount = database.file.getFileCount().getOrThrow()
    val fileVolume = database.file.getFileVolume().getOrThrow()
    val titleCount = database.title.getTitleCount().getOrThrow()

    PanelOverview(
        userCount,
        topicCount,
        communityCount,
        privateRoomCount,
        publicRoomCount,
        fileCount,
        fileVolume,
        titleCount,
    )
}

suspend fun Backend.addUser(newUser: NewUser): Result<UserInfo> {
    val nickname = newUser.nickname
    if (nickname != null && nickname.isBlank()) {
        return Result.failure(Exception(CustomBadRequestException("invalid nickname")))
    }
    runCatching {
        checkUserNickname(newUser)
        checkAid(newUser.aid, true).getOrThrow()
    }.exceptionOrNull()?.let {
        return Result.failure(it)
    }
    return runCatching {
        val authKey = newUser.authKey
        val algoType = authKey.algo
        val (address, pemPubKey) = getAlgo(algoType).run {
            val address = calcAddress(authKey.derPublicKey).getOrThrow()
            address to authKey.derPublicKey
        }
        val encPubKey = when (authKey) {
            is TransferAuthKey.Dilithium -> authKey.derEncryptionPublicKey
            is TransferAuthKey.P256 -> null
        }

        val id = SnowflakeFactory.nextId()
        val notificationId = SnowflakeFactory.nextId()
        database.user.createUser(
            User(
                newUser.aid,
                encPubKey,
                pemPubKey,
                address,
                null,
                nickname ?: nameService.parse(id),
                id,
                now(),
                0,
                PassType.RAW,
                algoType,
                notificationId
            )
        ).getOrThrow().toUserInfo()
    }
}

suspend fun Backend.updateUserStatus(
    uid: com.storyteller_f.shared.type.PrimaryKey,
    body: UpdateUserStatusBody,
    adminId: com.storyteller_f.shared.type.PrimaryKey?
): Result<Unit> {
    if (adminId == null) return Result.failure(UnauthorizedException())
    return database.user.updateUserStatus(uid, body.status).map {
        val logId = SnowflakeFactory.nextId()
        database.admin.insertPanelLog(
            PanelLog(
                id = logId,
                adminId = adminId,
                targetId = uid,
                objectType = ObjectType.USER,
                action = "Update user status to ${body.status}",
                createdTime = now()
            )
        )
    }
}

suspend fun Backend.updateCommunityStatus(
    id: com.storyteller_f.shared.type.PrimaryKey,
    body: UpdateObjectStatusBody,
    adminId: com.storyteller_f.shared.type.PrimaryKey?
): Result<Unit> {
    if (adminId == null) return Result.failure(UnauthorizedException())
    return database.community.updateCommunityStatus(id, body.status).map {
        val logId = SnowflakeFactory.nextId()
        database.admin.insertPanelLog(
            PanelLog(
                id = logId,
                adminId = adminId,
                targetId = id,
                objectType = ObjectType.COMMUNITY,
                action = "Update community status to ${body.status}",
                createdTime = now()
            )
        )
    }
}

suspend fun Backend.updateRoomStatus(
    id: com.storyteller_f.shared.type.PrimaryKey,
    body: UpdateObjectStatusBody,
    adminId: com.storyteller_f.shared.type.PrimaryKey?
): Result<Unit> {
    if (adminId == null) return Result.failure(UnauthorizedException())
    return database.room.updateRoomStatus(id, body.status).map {
        val logId = SnowflakeFactory.nextId()
        database.admin.insertPanelLog(
            PanelLog(
                id = logId,
                adminId = adminId,
                targetId = id,
                objectType = ObjectType.ROOM,
                action = "Update room status to ${body.status}",
                createdTime = now()
            )
        )
    }
}

suspend fun Backend.updateTopicStatus(
    id: com.storyteller_f.shared.type.PrimaryKey,
    body: UpdateObjectStatusBody,
    adminId: com.storyteller_f.shared.type.PrimaryKey?
): Result<Unit> {
    if (adminId == null) return Result.failure(UnauthorizedException())
    return database.topic.updateTopicStatus(id, body.status).map {
        val logId = SnowflakeFactory.nextId()
        database.admin.insertPanelLog(
            PanelLog(
                id = logId,
                adminId = adminId,
                targetId = id,
                objectType = ObjectType.TOPIC,
                action = "Update topic status to ${body.status}",
                createdTime = now()
            )
        )
    }
}

suspend fun Backend.updateTitleStatus(
    id: com.storyteller_f.shared.type.PrimaryKey,
    body: UpdateObjectStatusBody,
    adminId: com.storyteller_f.shared.type.PrimaryKey?
): Result<Unit> {
    if (adminId == null) return Result.failure(UnauthorizedException())
    return database.title.updateTitleStatus(id, body.status).map {
        val logId = SnowflakeFactory.nextId()
        database.admin.insertPanelLog(
            PanelLog(
                id = logId,
                adminId = adminId,
                targetId = id,
                objectType = ObjectType.TITLE,
                action = "Update title status to ${body.status}",
                createdTime = now()
            )
        )
    }
}

suspend fun Backend.updateFileStatus(
    id: com.storyteller_f.shared.type.PrimaryKey,
    body: UpdateObjectStatusBody,
    adminId: com.storyteller_f.shared.type.PrimaryKey?
): Result<Unit> {
    if (adminId == null) return Result.failure(UnauthorizedException())
    return database.file.updateFileStatus(id, body.status).map {
        val logId = SnowflakeFactory.nextId()
        database.admin.insertPanelLog(
            PanelLog(
                id = logId,
                adminId = adminId,
                targetId = id,
                objectType = ObjectType.FILE,
                action = "Update file status to ${body.status}",
                createdTime = now()
            )
        )
    }
}

suspend fun Backend.getPanelLogs(
    targetId: com.storyteller_f.shared.type.PrimaryKey,
    objectType: ObjectType,
    fetch: PrimaryKeyFetch
): Result<PaginationResult<PanelLogInfo>> {
    return database.admin.getPanelLogs(targetId, objectType, fetch).map { result ->
        PaginationResult(result.list.map { it.toPanelLogInfo() }, result.total)
    }
}

suspend fun Backend.getTaskRecords(
    type: TaskRecordType?,
    fetch: PrimaryKeyFetch
): Result<PaginationResult<TaskRecordInfo>> {
    return database.admin.getTaskRecords(type, fetch).map { result ->
        PaginationResult(result.list.map { it.toTaskRecordInfo() }, result.total)
    }
}
