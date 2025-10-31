package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.NewDevice
import com.storyteller_f.a.backend.core.AID_LENGTH
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.USER_NICKNAME
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.service.UserDocumentSearch
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.model.checkMediaFileDimensionRatioMatch
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
        checkIcon(newUser.avatar, Dimension.DEFAULT_DIMENSION).mapResult {
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
    return database.user.updateUserInfo(uid, newUser).mapResult {
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
    database.user.getUserAid(id).mapResult {
        if (it != null) {
            Result.failure(CustomBadRequestException("aid is not null."))
        } else {
            UNIT_RESULT
        }
    }
}

fun checkAid(aid: String?, supportEmptyAid: Boolean = false): Result<Unit> {
    if (aid.isNullOrBlank()) {
        if (supportEmptyAid) {
            return UNIT_RESULT
        }
        return Result.failure(CustomBadRequestException("aid is empty"))
    }
    if (aid.length !in 2..AID_LENGTH) {
        return Result.failure(CustomBadRequestException("aid too long or too short"))
    }

    if (aid.all {
            !it.isEnglishLetter()
        }) {
        return Result.failure(CustomBadRequestException("aid must contains english letter"))
    }
    if (!aid.all {
            it.isEnglishLetter() || it.isDigit() || it == '_' || it == '-'
        }) {
        return Result.failure(CustomBadRequestException("only support alphabet, number, underline and hyphen"))
    }
    return UNIT_RESULT
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
        database.file.getFileRecordByIds(listOf(icon)).mapIfNotNull {
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
            database.user.addReadLog(
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

suspend fun Backend.addChildAccount(uid: PrimaryKey): Result<ChildAccountInfo> {
    return database.user.getRawChildAccount(uid).mapResult {
        if (it != null) {
            Result.failure(CustomBadRequestException("child account can't create child account"))
        } else {
            runCatching {
                val (publicKey, address, derPrivateKey) = getAlgo().run {
                    val pemPrivateKey = generateECDSAPemPrivateKey().getOrThrow()
                    val publicKey = getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
                    val derPrivateKey = getDerPrivateKey(pemPrivateKey).getOrThrow()
                    val address = calcAddress(publicKey).getOrThrow()
                    Triple(publicKey, address, derPrivateKey)
                }
                val id = SnowflakeFactory.nextId()
                val notificationId = SnowflakeFactory.nextId()
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
                    AlgoType.P256,
                    notificationId
                )
                database.user.createChildAccount(uid, derPrivateKey, user)
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
    database.user.insertUserLog(log).onFailure {
        Napier.i(tag = "user log", throwable = it) {
            "add failed"
        }
    }
}

suspend fun Backend.addDevice(
    uid: PrimaryKey,
    newDevice: NewDevice
): Result<Unit> =
    database.user.addDevice(uid, newDevice.endpointUrl).recoverResult {
        if (database.isDup(it)) {
            UNIT_RESULT
        } else {
            Result.failure(it)
        }
    }

suspend fun Backend.getUserAlternateUserInfoList(
    uid: PrimaryKey,
    fetch: PrimaryKeyFetch,
): Result<PaginationResult<ChildAccountInfo>?> {
    return database.user.getRawChildAccountPaginationListByHost(
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
    return database.container.getJoinedUserList(roomId).map { value ->
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
        database.container.getMemberPaginationResult(
            objectId,
            word,
            primaryKeyFetch
        )
    } else {
        userSearchService.searchDocument(UserDocumentSearch.Keyword(listOf(word)), primaryKeyFetch)
            .mapResult { (list, total) ->
                database.user.getRawUsers(ObjectListFetch.IdListFetch(list.map {
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
    return database.user.getRawUsers(listFetch).mapResult {
        processRawUserToUserInfo(it)
    }
}

suspend fun Backend.getUserInfo(
    fetch: ObjectFetch,
): Result<UserInfo?> {
    return database.user.getRawUser(fetch).mapResultIfNotNull {
        processRawUserToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun Backend.processRawUserToUserInfo(
    rawResults: List<RawUser>,
) = database.file.getFileRecordByIds(rawResults.mapNotNull {
    it.user.icon
}).mapResult { medias ->
    processFileRecordToFileInfo(medias).map { list ->
        val mediaInfoMap = list.associateBy { it.id }
        rawResults.map { pair ->
            pair.user.toUserInfo().copy(avatar = pair.user.icon?.let { mediaInfoMap[it] })
        }
    }
}

suspend fun Backend.getAllUsers(primaryKeyFetch: PrimaryKeyFetch) =
    database.user.getAllUsers(primaryKeyFetch).mapResult { result ->
        processRawUserToUserInfo(result.list).map {
            PaginationResult(it, result.total)
        }
    }

fun Char.isEnglishLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}
