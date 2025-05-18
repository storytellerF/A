package com.storyteller_f.a.server.service

import com.storyteller_f.*
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.model.checkMediaDimensionRatioMatch
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.ktor.server.plugins.*

suspend fun updateUser(
    backend: Backend,
    uid: PrimaryKey,
    old: UpdateUserBody
): Result<UserInfo?> {
    val newUser = old.copy(nickname = old.nickname?.trim(), aid = old.aid?.trim(), avatar = old.avatar?.trim())
    val firstError = listOf(suspend {
        checkAidModifyTimes(backend, newUser, uid)
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
        checkIcon(backend, newUser.avatar).mapResult {
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
    return DatabaseFactory.updateUser(backend, uid, newUser).mapResult {
        if (it) {
            addUserLog(backend, uid, UserLogType.UPDATE, uid ob ObjectType.USER)
            DatabaseFactory.getUser(backend, ObjectFetch.IdFetch(uid))
        } else {
            Result.success(null)
        }
    }
}

private suspend fun checkAidModifyTimes(
    backend: Backend,
    newUser: UpdateUserBody,
    id: PrimaryKey
) = if (newUser.aid.isNullOrBlank()) {
    Result.success(Unit)
} else {
    // check aid is null
    DatabaseFactory.getUserAid(backend, id).mapResult {
        if (it != null) {
            Result.failure(BadRequestException("aid is not null."))
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

        else -> Result.failure(BadRequestException("aid too long"))
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

suspend fun checkIcon(
    backend: Backend,
    iconName: String?,
    aspectRatio: Dimension? = null
): Result<MediaCheckResult?> {
    return if (!iconName.isNullOrBlank()) {
        DatabaseFactory.getMediaInfoList(backend, listOf(iconName)).mapIfNotNull {
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

suspend fun addReadLog(backend: Backend, uid: PrimaryKey, tuple: UpdateUserRead): Result<Unit?> {
    return checkRootReadPermission(
        backend,
        tuple.objectTuple.objectType,
        tuple.objectTuple.objectId,
        uid
    ).mapResultIfNotNull {
        if (it.hasRead) {
            DatabaseFactory.addReadLog(
                backend,
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

suspend fun addDevice(backend: Backend, uid: PrimaryKey, device: String): Result<Unit> {
    return DatabaseFactory.addDevice(uid, device, backend).recover {
        if (it.isDup()) {
            Result.success(Unit)
        } else {
            Result.failure(it)
        }
    }
}