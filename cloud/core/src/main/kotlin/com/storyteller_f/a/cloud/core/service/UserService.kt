package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.a.backend.exposed.AID_LENGTH
import com.storyteller_f.a.backend.exposed.USER_NICKNAME
import com.storyteller_f.a.backend.exposed.isDup
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.getUserInfo
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.generateECDSAPemPrivateKey
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.NewDevice
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier

suspend fun Backend.updateUser(
    uid: PrimaryKey,
    old: UpdateUserBody,
): Result<UserInfo?> {
    val newUser = old.copy(nickname = old.nickname?.trim(), aid = old.aid?.trim(), avatar = old.avatar)
    val firstError = listOf(suspend {
        checkAidModifyTimes(newUser, uid)
    }, suspend {
        checkAid(newUser.aid, true)
    }, suspend {
        when (checkNickname(newUser.nickname, 1..USER_NICKNAME)) {
            StringCheckResult.RANGE_MISMATCH -> Result.failure(
                CustomBadRequestException("user nickname must be between in 1 and 20")
            )

            else -> Result.success(Unit)
        }
    }, suspend {
        checkIcon(newUser.avatar).mapResult {
            when (it) {
                MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("avatar not valid"))
                MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                    CustomBadRequestException("avatar must be image")
                )

                else -> Result.success(Unit)
            }
        }
    }).firstNotNullOfOrNull {
        it().exceptionOrNull()
    }
    if (firstError != null) return Result.failure(firstError)
    return exposedDatabase.userDatabase.updateUserInfo(uid, newUser).mapResult {
        if (it) {
            addUserLog(uid, UserLogType.UPDATE, uid ob ObjectType.USER)
            getUserInfo(ObjectFetch.IdFetch(uid))
        } else {
            Result.success(null)
        }
    }
}

private suspend fun Backend.checkAidModifyTimes(
    newUser: UpdateUserBody,
    id: PrimaryKey,
) = if (newUser.aid.isNullOrBlank()) {
    Result.success(Unit)
} else {
    // check aid is null
    exposedDatabase.userDatabase.getUserAid(id).mapResult {
        if (it != null) {
            Result.failure(CustomBadRequestException("aid is not null."))
        } else {
            Result.success(Unit)
        }
    }
}

fun checkAid(aid: String?, supportEmptyAid: Boolean = false): Result<Unit> {
    return when {
        aid.isNullOrBlank() -> if (supportEmptyAid) {
            Result.success(Unit)
        } else {
            Result.failure(
                CustomBadRequestException("aid is empty")
            )
        }

        aid.length in 2..AID_LENGTH -> Result.success(Unit)
        !aid.all {
            it.isLetterOrDigit() || it == '_' || it == '-'
        } -> {
            Result.failure(CustomBadRequestException("only support alphabet, number, underline and hyphen"))
        }

        else -> Result.failure(CustomBadRequestException("aid too long"))
    }
}

fun checkNickname(nickname: String?, range: IntRange): StringCheckResult {
    return when {
        nickname.isNullOrBlank() -> (StringCheckResult.EMPTY)
        nickname.length in range -> (StringCheckResult.SUCCESS)
        else -> StringCheckResult.RANGE_MISMATCH
    }
}

enum class MediaCheckResult {
    EMPTY,
    NOT_FOUND,
    CONTENT_TYPE_MISMATCH,
    SUCCESS,
    DIMENSION_MISMATCH,
}

enum class StringCheckResult {
    EMPTY,
    SUCCESS,
    RANGE_MISMATCH
}

suspend fun Backend.checkIcon(
    iconName: PrimaryKey?,
    aspectRatio: Dimension? = null,
): Result<MediaCheckResult?> {
    return if (iconName != null) {
        exposedDatabase.mediaDatabase.getMediaByIds(listOf(iconName)).mapIfNotNull {
            val mediaInfo = it.firstOrNull()
            val dimension = mediaInfo?.dimension
            when {
                mediaInfo == null -> MediaCheckResult.NOT_FOUND
                !mediaInfo.contentType.startsWith("image/") -> MediaCheckResult.CONTENT_TYPE_MISMATCH
                aspectRatio != null && (dimension == null || !checkMediaDimensionRatioMatch(
                    dimension,
                    aspectRatio
                )) -> MediaCheckResult.DIMENSION_MISMATCH

                else -> MediaCheckResult.SUCCESS
            }
        }
    } else {
        Result.success(MediaCheckResult.EMPTY)
    }
}

suspend fun Backend.addReadLog(uid: PrimaryKey, tuple: UpdateUserRead): Result<Unit?> {
    return checkRootReadPermission(
        tuple.objectTuple.objectType,
        tuple.objectTuple.objectId,
        uid
    ).mapResultIfNotNull {
        if (it.hasRead) {
            exposedDatabase.userDatabase.addReadLog(
                UserTopicRead(
                    uid,
                    now(),
                    tuple.objectTuple.objectId,
                    tuple.objectTuple.objectType,
                    tuple.topicId
                )
            )
        } else {
            Result.failure(ForbiddenException("Permission denied"))
        }
    }
}

suspend fun Backend.addAlternativeAccount(uid: PrimaryKey): Result<AlternativeAccountInfo> {
    return generateECDSAPemPrivateKey().mapResult { pemPrivateKey ->
        getDerPrivateKey(pemPrivateKey).mapResult { derPrivateKey ->
            getDerPublicKeyFromPrivateKey(pemPrivateKey).mapResult { publicKey ->
                calcAddress(publicKey).mapResult { address ->
                    val id = SnowflakeFactory.nextId()
                    val user = User(
                        null,
                        publicKey,
                        address,
                        null,
                        nameService.parse(id),
                        id,
                        now(),
                        0,
                        PassType.RAW,
                        AlgoType.P256
                    )
                    exposedDatabase.userDatabase.createAlternativeAccount(uid, derPrivateKey, user).map {
                        addUserLog(uid, UserLogType.ADD_ALTERNATIVE_ACCOUNT, id ob ObjectType.USER)
                        AlternativeAccountInfo(uid, derPrivateKey, user.toUserInfo())
                    }
                }
            }
        }
    }
}

suspend fun Backend.addUserLog(uid: PrimaryKey, type: UserLogType, objectTuple: ObjectTuple) {
    val logId = SnowflakeFactory.nextId()
    val log = UserLog(logId, now(), uid, type, objectTuple.objectId, objectTuple.objectType)
    exposedDatabase.userDatabase.insertUserLog(log).onFailure {
        Napier.i(tag = "user log", throwable = it) {
            "add failed"
        }
    }
}

suspend fun addDevice(
    backend: Backend,
    uid: PrimaryKey,
    newDevice: NewDevice
): Result<Unit> =
    backend.exposedDatabase.userDatabase.addDevice(uid, newDevice.endpointUrl).recoverResult {
        if (it.isDup()) {
            Result.success(Unit)
        } else {
            Result.failure(it)
        }
    }
