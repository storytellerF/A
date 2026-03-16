package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewUser
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.PanelLog
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.UpdateUserStatusBody
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
        val (address, pemPubKey) = getAlgo(newUser.algoType).run {
            val address = calcAddress(newUser.publicKey).getOrThrow()
            address to newUser.publicKey
        }
        val (encPubKey, encPrivKey) = if (newUser.algoType == AlgoType.DILITHIUM) {
            val algo = getAlgo(newUser.algoType)
            val (priKey, pubKey) = algo.generateEncryptionPemKeyPair().getOrThrow()
            val derPriKey = algo.getDerPrivateKey(priKey).getOrThrow()
            val derPubKey = algo.getDerPublicKeyFromPem(pubKey).getOrThrow()
            derPubKey to derPriKey
        } else {
            null to null
        }

        val id = SnowflakeFactory.nextId()
        val notificationId = SnowflakeFactory.nextId()
        database.user.createUser(
            User(
                newUser.aid,
                encPubKey,
                encPrivKey,
                pemPubKey,
                address,
                null,
                nickname ?: nameService.parse(id),
                id,
                now(),
                0,
                PassType.RAW,
                AlgoType.P256,
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
                targetUserId = uid,
                action = "Update user status to ${body.status}",
                createdTime = now()
            )
        )
    }
}
