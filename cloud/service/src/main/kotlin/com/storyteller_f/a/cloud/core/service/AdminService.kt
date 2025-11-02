package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.NewUser
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PanelOverview
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now

suspend fun Backend.getOverview() = runCatching {
    val userCount = database.user.getUserCount().getOrThrow()
    val topicCount = database.topic.getTopicCount().getOrThrow()
    val communityCount = database.community.getCommunityCount().getOrThrow()
    val privateRoomCount = database.room.getPrivateRoomCount().getOrThrow()
    val publicRoomCount = database.room.getPublicRoomCount().getOrThrow()
    val fileCount = database.file.getFileCount().getOrThrow()
    val fileVolume = database.file.getFileVolume().getOrThrow()
    PanelOverview(
        userCount,
        topicCount,
        communityCount,
        privateRoomCount,
        publicRoomCount,
        fileCount,
        fileVolume
    )
}

suspend fun Backend.addUser(newUser: NewUser): Result<UserInfo> {
    val nickname = newUser.nickname
    if (nickname != null && nickname.isBlank()) {
        return Result.failure(Exception(CustomBadRequestException("invalid nickname")))
    }
    runCatching {
        checkUserNickname(newUser.nickname)
        checkAid(newUser.aid, true).getOrThrow()
    }.exceptionOrNull()?.let {
        return Result.failure(it)
    }
    return getAlgo().calcAddress(newUser.publicKey).mapResult { address ->
        val id = SnowflakeFactory.nextId()
        val notificationId = SnowflakeFactory.nextId()
        database.user.createUser(
            User(
                newUser.aid,
                newUser.publicKey,
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
        )
    }.map {
        it.toUserInfo()
    }
}
