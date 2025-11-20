package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewDevice
import com.storyteller_f.a.api.NewUser
import com.storyteller_f.a.backend.core.AID_LENGTH
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.USER_NICKNAME
import com.storyteller_f.a.backend.core.pagingNotNull
import com.storyteller_f.a.backend.core.service.MemberDocumentSearch
import com.storyteller_f.a.backend.core.service.UserDocument
import com.storyteller_f.a.backend.core.service.UserDocumentSearch
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.RawUserOverview
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.core.types.toUserInfo
import com.storyteller_f.a.backend.core.types.toUserLogInfo
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.ChildAccountInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MemberInfo
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.model.UserLogInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.model.UserOverview
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
import com.storyteller_f.shared.utils.recoverIfDup
import io.github.aakira.napier.Napier

suspend fun Backend.updateUser(
    uid: PrimaryKey,
    old: UpdateUserBody,
): Result<UserInfo?> {
    val newUpdate =
        old.copy(nickname = old.nickname?.trim(), aid = old.aid?.trim(), avatar = old.avatar)
    runCatching {
        checkAidModifyTimes(newUpdate, uid).getOrThrow()
        checkAid(newUpdate.aid, true).getOrThrow()
        checkUserNickname(old).getOrThrow()
        checkUserIcon(newUpdate)
    }.exceptionOrNull()?.let {
        return Result.failure(it)
    }
    return database.user.updateUserInfo(uid, newUpdate).mapResult {
        if (it) {
            addUserLog(uid, UserLogType.UPDATE, uid ob ObjectType.USER)
            getUserInfo(ObjectFetch.IdFetch(uid))
        } else {
            Result.success(null)
        }
    }
}

private suspend fun Backend.checkUserIcon(newUser: UpdateUserBody) {
    checkIcon(newUser.avatar, Dimension.DEFAULT_DIMENSION).mapResult {
        when (it) {
            MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("avatar not font"))
            MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                CustomBadRequestException("avatar must be image")
            )

            else -> UNIT_RESULT
        }
    }.getOrThrow()
}

fun checkUserNickname(newUser: NewUser) {
    when (checkNickname(
        newUser.nickname,
        1..USER_NICKNAME
    )) {
        StringCheckResult.CONTAIN_INVALID_CHAR -> throw CustomBadRequestException(
            "user nickname must not contain invalid char"
        )

        StringCheckResult.RANGE_MISMATCH -> throw CustomBadRequestException("user nickname must be between in 1 and 20")
        else -> UNIT_RESULT
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
    val underlineCount = aid.count { it == '_' || it == '-' }
    if (underlineCount > 1.0 / aid.length) {
        return Result.failure(CustomBadRequestException("aid contains too many underline or hyphen"))
    }
    return UNIT_RESULT
}

fun checkNickname(nickname: String?, range: IntRange): StringCheckResult {
    if (nickname == null) return StringCheckResult.NULL
    if (nickname.isBlank()) return StringCheckResult.EMPTY
    if (nickname.length !in range) return StringCheckResult.RANGE_MISMATCH
    if (!isAllVisibleChar(nickname)) return StringCheckResult.CONTAIN_INVALID_CHAR
    return StringCheckResult.SUCCESS
}

/**
 * 接受空字符串或者null
 */
fun checkUserNickname(update: UpdateUserBody): Result<Unit> {
    return when (checkNickname(update.nickname, 1..USER_NICKNAME)) {
        StringCheckResult.RANGE_MISMATCH -> Result.failure(
            CustomBadRequestException("user nickname must be between in 1 and $USER_NICKNAME")
        )

        StringCheckResult.CONTAIN_INVALID_CHAR -> Result.failure(
            CustomBadRequestException("user nickname must be visible")
        )

        else -> UNIT_RESULT
    }
}

fun isAllVisibleChar(s: String) = !s.codePoints().anyMatch { codePoint ->
    val type = Character.getType(codePoint)
    Character.isISOControl(codePoint) ||
        Character.getType(codePoint).toByte() == Character.FORMAT ||
        !Character.isDefined(codePoint) ||
        type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        Character.isWhitespace(codePoint) ||
        type == Character.SPACE_SEPARATOR.toInt()
}

enum class MediaCheckResult {
    EMPTY,
    NOT_FOUND,
    CONTENT_TYPE_MISMATCH,
    SUCCESS,
    DIMENSION_MISMATCH,
}

enum class StringCheckResult {
    NULL,
    EMPTY,
    SUCCESS,
    RANGE_MISMATCH,
    CONTAIN_INVALID_CHAR,
}

suspend fun Backend.checkIcon(
    icon: PrimaryKey?,
    aspectRatio: Dimension? = null,
): Result<MediaCheckResult?> {
    if (icon == null) {
        return Result.success(MediaCheckResult.EMPTY)
    }
    return database.file.getFileRecordByIds(listOf(icon)).mapIfNotNull {
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
                    val pemPrivateKey = generatePemKeyPair().getOrThrow().first
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
    database.user.addDevice(uid, newDevice.endpointUrl).recoverIfDup(database::isDup) {
        UNIT_RESULT
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

/**
 * 搜索 room/community 的成员列表
 * @param objectId 容器 ID（room 或 community）
 * @param word 搜索关键字，可选
 * @return 返回 MemberInfo 列表，包含成员关系信息
 */
suspend fun Backend.searchContainerMembers(
    objectId: PrimaryKey,
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<MemberInfo>> {
    val result = if (word.isNullOrBlank()) {
        // 无关键字，直接获取成员列表
        database.container.getMemberWithUserPaginationResult(objectId, primaryKeyFetch)
    } else {
        // 有关键字，使用 MemberSearchService 搜索
        memberSearchService.searchDocument(
            MemberDocumentSearch.Keyword(objectId = objectId, nickname = word),
            primaryKeyFetch
        ).mapResult { (searchResults, total) ->
            // 从搜索结果中提取 uid 列表
            val uidList = searchResults.map { it.uid }
            // 获取 member 和 user 信息
            database.container.getMemberWithUserByUids(objectId, uidList).map { memberUserPairs ->
                PaginationResult(memberUserPairs, total)
            }
        }
    }

    return result.mapResult { (list, total) ->
        val rawUsers = list.map { it.second }
        processRawUserToUserInfo(rawUsers).map { users ->
            val userMap = users.associateBy { it.id }
            PaginationResult(
                list.map { (member, rawUser) ->
                    MemberInfo(
                        id = member.id,
                        uid = member.uid,
                        objectId = member.objectId,
                        objectType = member.objectType,
                        status = member.status,
                        joinedTime = (member.joinedTime ?: member.createdTime).date,
                        invitedTime = member.invitedTime?.date,
                        userInfo = userMap[rawUser.user.id]!!
                    )
                },
                total
            )
        }
    }
}

/**
 * 搜索用户
 * @param word 搜索关键字
 * @return 返回 UserInfo 列表
 */
suspend fun Backend.searchUsers(
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch,
): Result<PaginationResult<UserInfo>?> {
    if (word.isNullOrBlank()) {
        return Result.success(null)
    }
    return userSearchService.searchDocument(
        UserDocumentSearch.Keyword(listOf(word)),
        primaryKeyFetch
    ).mapResult { (list, total) ->
        database.user.getRawUsers(ObjectListFetch.IdListFetch(list.map {
            it.id
        })).mapResult {
            processRawUserToUserInfo(it).map { users ->
                PaginationResult(users, total)
            }
        }
    }
}

suspend fun Backend.getUserInfoList(
    listFetch: ObjectListFetch,
) = database.user.getRawUsers(listFetch).mapResult {
    processRawUserToUserInfo(it)
}

suspend fun Backend.getUserInfo(
    fetch: ObjectFetch,
) = database.user.getRawUser(fetch).mapResultIfNotNull {
    processRawUserToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
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

suspend fun Backend.getUserOverview(uid: PrimaryKey) = database.getUserOverview(uid).mapResult {
    processRawUserOverviewToUserOverview(it)
}

suspend fun Backend.processRawUserOverviewToUserOverview(raw: RawUserOverview): Result<UserOverview> {
    return processRawUserToUserInfo(listOf(raw.rawUser)).map { users ->
        val userInfo = users.first()
        UserOverview(
            raw.subscriptionCount,
            raw.favoriteCount,
            raw.acg,
            raw.childAccountCount,
            userInfo
        )
    }
}

suspend fun Backend.getAllUsers(primaryKeyFetch: PrimaryKeyFetch) =
    database.user.getAllUsers(primaryKeyFetch).mapResult { result ->
        processRawUserToUserInfo(result.list).map {
            PaginationResult(it, result.total)
        }
    }

suspend fun Backend.getUserById(id: PrimaryKey) = getUserInfo(ObjectFetch.IdFetch(id))

suspend fun Backend.getUserLogs(
    uid: PrimaryKey,
    fetch: PrimaryKeyFetch
) = database.user.getUserLogs(uid, fetch).mapResult { (list, total) ->
    processUserLogToUserLogInfo(list, uid).pagingNotNull(total)
}

private suspend fun Backend.processUserLogToUserLogInfo(
    list: List<UserLog>,
    uid: PrimaryKey
) = runCatching {
    val userIds = mutableListOf<PrimaryKey>()
    val communityIds = mutableListOf<PrimaryKey>()
    val roomIds = mutableListOf<PrimaryKey>()
    val topicIds = mutableListOf<PrimaryKey>()
    list.forEach { log ->
        when (log.objectType) {
            ObjectType.USER -> userIds += log.objectId
            ObjectType.COMMUNITY -> communityIds += log.objectId
            ObjectType.ROOM -> roomIds += log.objectId
            ObjectType.TOPIC -> topicIds += log.objectId
            else -> {}
        }
    }
    val users = getUserInfoList(ObjectListFetch.IdListFetch(userIds)).getOrThrow()
    val communities =
        database.community.getRawCommunities(ObjectListFetch.IdListFetch(communityIds))
            .mapResult { processRawCommunityToCommunityInfo(it) }.getOrThrow()
            ?: emptyList()
    val rooms = getRoomInfoList(ObjectListFetch.IdListFetch(roomIds)).getOrThrow()
    val topics = getTopicByIds(topicIds, uid).getOrThrow() ?: emptyList()
    val userMap = users.associateBy { it.id }
    val communityMap = communities.associateBy { it.id }
    val roomMap = rooms.associateBy { it.id }
    val topicMap = topics.associateBy { it.id }
    list.map { log ->
        val ext = when (log.objectType) {
            ObjectType.USER -> UserLogInfo.Extensions(user = userMap[log.objectId])
            ObjectType.COMMUNITY -> UserLogInfo.Extensions(community = communityMap[log.objectId])
            ObjectType.ROOM -> UserLogInfo.Extensions(room = roomMap[log.objectId])
            ObjectType.TOPIC -> UserLogInfo.Extensions(topic = topicMap[log.objectId])
            else -> null
        }
        log.toUserLogInfo().copy(extensions = ext)
    }
}

fun Char.isEnglishLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}
