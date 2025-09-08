package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.a.backend.exposed.AID_LENGTH
import com.storyteller_f.a.backend.exposed.USER_NICKNAME
import com.storyteller_f.a.backend.exposed.isDup
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.processRawUserToUserInfo
import com.storyteller_f.a.backend.service.search.UserDocument
import com.storyteller_f.a.backend.service.search.UserDocumentSearch
import com.storyteller_f.shared.calcAddress
import com.storyteller_f.shared.generateECDSAPemPrivateKey
import com.storyteller_f.shared.getDerPrivateKey
import com.storyteller_f.shared.getDerPublicKeyFromPrivateKey
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.model.checkMediaFileDimensionRatioMatch
import com.storyteller_f.shared.obj.NewDevice
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
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
    val newUser =
        old.copy(nickname = old.nickname?.trim(), aid = old.aid?.trim(), avatar = old.avatar)
    val firstError = listOf(suspend {
        checkAidModifyTimes(newUser, uid)
    }, suspend {
        checkAid(newUser.aid, true)
    }, suspend {
        when (checkNickname(newUser.nickname, 1..USER_NICKNAME)) {
            StringCheckResult.RANGE_MISMATCH -> Result.failure(
                CustomBadRequestException("user nickname must be between in 1 and 20")
            )

            else -> UNIT_RESULT
        }
    }, suspend {
        checkIcon(newUser.avatar).mapResult {
            when (it) {
                MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("avatar not font"))
                MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                    CustomBadRequestException("avatar must be image")
                )

                else -> UNIT_RESULT
            }
        }
    }).firstNotNullOfOrNull {
        it().exceptionOrNull()
    }
    if (firstError != null) return Result.failure(firstError)
    return combinedDatabase.userDatabase.updateUserInfo(uid, newUser).mapResult {
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
    UNIT_RESULT
} else {
    // check aid is null
    combinedDatabase.userDatabase.getUserAid(id).mapResult {
        if (it != null) {
            Result.failure(CustomBadRequestException("aid is not null."))
        } else {
            UNIT_RESULT
        }
    }
}

fun checkAid(aid: String?, supportEmptyAid: Boolean = false): Result<Unit> {
    return when {
        aid.isNullOrBlank() -> if (supportEmptyAid) {
            UNIT_RESULT
        } else {
            Result.failure(
                CustomBadRequestException("aid is empty")
            )
        }

        aid.length in 2..AID_LENGTH -> UNIT_RESULT
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
    icon: PrimaryKey?,
    aspectRatio: Dimension? = null,
): Result<MediaCheckResult?> {
    return if (icon != null) {
        combinedDatabase.fileDatabase.getFileRecordByIds(listOf(icon)).mapIfNotNull {
            val mediaInfo = it.firstOrNull()
            val dimension = mediaInfo?.dimension
            when {
                mediaInfo == null -> MediaCheckResult.NOT_FOUND
                !mediaInfo.contentType.startsWith("image/") -> MediaCheckResult.CONTENT_TYPE_MISMATCH
                aspectRatio != null && (dimension == null || !checkMediaFileDimensionRatioMatch(
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
            combinedDatabase.userDatabase.addReadLog(
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

suspend fun Backend.addAlternativeAccount(uid: PrimaryKey): Result<ChildAccountInfo> {
    return combinedDatabase.userDatabase.getRawChildAccount(uid).mapResult {
        if (it != null) {
            Result.failure(CustomBadRequestException("alternative account can't create alternative"))
        } else {
            runCatching {
                val pemPrivateKey = generateECDSAPemPrivateKey().getOrThrow()
                val publicKey = getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
                val derPrivateKey = getDerPrivateKey(pemPrivateKey).getOrThrow()
                val address = calcAddress(publicKey).getOrThrow()
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
                combinedDatabase.userDatabase.createChildAccount(uid, derPrivateKey, user)
                    .getOrThrow()
                userSearchService.saveDocument(listOf(UserDocument.fromUser(user)))
                    .onFailure { throwable ->
                        Napier.e(throwable) {
                            "save user document failed"
                        }
                    }
                addUserLog(
                    uid,
                    UserLogType.ADD_ALTERNATIVE_ACCOUNT,
                    id ob ObjectType.USER
                )
                ChildAccountInfo(uid, derPrivateKey, user.toUserInfo())
            }
        }
    }
}

suspend fun Backend.addUserLog(uid: PrimaryKey, type: UserLogType, objectTuple: ObjectTuple) {
    val logId = SnowflakeFactory.nextId()
    val log = UserLog(logId, now(), uid, type, objectTuple.objectId, objectTuple.objectType)
    combinedDatabase.userDatabase.insertUserLog(log).onFailure {
        Napier.i(tag = "user log", throwable = it) {
            "add failed"
        }
    }
}

suspend fun Backend.addDevice(
    uid: PrimaryKey,
    newDevice: NewDevice
): Result<Unit> =
    combinedDatabase.userDatabase.addDevice(uid, newDevice.endpointUrl).recoverResult {
        if (it.isDup()) {
            UNIT_RESULT
        } else {
            Result.failure(it)
        }
    }

suspend fun Backend.getUserAlternateUserInfoList(
    uid: PrimaryKey,
    fetch: PrimaryKeyFetch,
): Result<PaginationResult<ChildAccountInfo>?> {
    return combinedDatabase.userDatabase.getRawChildAccountPaginationListByHost(
        uid,
        fetch
    ).mapResult { (results, total) ->
        processRawUserToUserInfo(results.map {
            it.rawUser
        }).mapIfNotNull { userList ->
            val map = userList.associateBy { it.id }
            PaginationResult(results.mapNotNull {
                map[it.rawUser.user.id]?.let { userInfo ->
                    ChildAccountInfo(
                        it.rawUser.user.id,
                        it.childAccount.privateKey,
                        userInfo
                    )
                }
            }, total)
        }
    }
}

suspend fun Backend.isKeyVerified(
    roomId: PrimaryKey,
    encryptedAes: Map<PrimaryKey, String>,
): Result<Boolean> {
    return combinedDatabase.containerDatabase.getJoinedUserList(roomId).map { value ->
        value.map {
            it.uid
        }.toSet().minus(encryptedAes.keys).isEmpty()
    }
}

suspend fun Backend.searchMembers(
    objectId: PrimaryKey?,
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<UserInfo>?> {
    return if (word.isNullOrBlank()) {
        combinedDatabase.containerDatabase.getMemberPaginationResult(
            objectId,
            word,
            primaryKeyFetch
        )
    } else {
        userSearchService.searchDocument(UserDocumentSearch.Keyword(listOf(word)), primaryKeyFetch)
            .mapResult { (list, total) ->
                combinedDatabase.userDatabase.getRawUsers(ObjectListFetch.IdListFetch(list.map {
                    it.id
                })).map {
                    PaginationResult(it, total)
                }
            }
    }.mapResult { (rawUsers, count) ->
        processRawUserToUserInfo(rawUsers).map {
            PaginationResult(it, count)
        }
    }
}

suspend fun Backend.getUserInfoList(
    listFetch: ObjectListFetch,
): Result<List<UserInfo>> {
    return combinedDatabase.userDatabase.getRawUsers(listFetch).mapResult {
        processRawUserToUserInfo(it)
    }
}

suspend fun Backend.getUserInfo(
    fetch: ObjectFetch,
): Result<UserInfo?> {
    return combinedDatabase.userDatabase.getRawUser(fetch).mapResultIfNotNull {
        processRawUserToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}
