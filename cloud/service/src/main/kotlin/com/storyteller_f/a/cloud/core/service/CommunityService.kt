package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.service.CommunityDocument
import com.storyteller_f.a.backend.core.service.CommunityDocumentSearch
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.RawCommunity
import com.storyteller_f.a.backend.core.types.toCommunityIfo
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.errorIfFalse
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier

suspend fun Backend.getCommunity(
    objectFetch: ObjectFetch,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return database.community.getRawCommunity(
        objectFetch,
        fillJoinInfo,
        id
    ).mapResultIfNotNull {
        processRawCommunityToCommunityInfo(listOf(it)).mapIfNotNull(List<CommunityInfo>::first)
    }
}

suspend fun Backend.joinCommunity(
    uid: PrimaryKey,
    communityId: PrimaryKey
) = getCommunity(
    ObjectFetch.IdFetch(communityId),
    uid,
    true
).mapResultIfNotNull { community ->
    if (community.joinedTime != null) {
        Result.success(community)
    } else {
        if (community.memberPolicy == MemberPolicy.INVITE_ONLY) {
            getJoinTitleByScope(uid, communityId)
        } else {
            UNIT_RESULT
        }.mapResult {
            joinCommunity(uid, communityId, community)
        }
    }
}

private suspend fun Backend.joinCommunity(
    uid: PrimaryKey,
    communityId: PrimaryKey,
    community: CommunityInfo
): Result<CommunityInfo?> {
    val time = now()
    val memberId = SnowflakeFactory.nextId()
    val member = Member(
        memberId,
        uid,
        communityId,
        ObjectType.COMMUNITY,
        time,
        MemberStatus.JOINED,
        time
    )
    return database.container.joinContainer(member).mapResult {
        addUserLog(uid, UserLogType.JOIN, communityId ob ObjectType.COMMUNITY)
        Result.success(community.copy(joinedTime = time))
    }.recoverResult {
        if (database.isDup(it)) {
            getCommunity(ObjectFetch.IdFetch(communityId), uid, true)
        } else {
            Result.failure(it)
        }
    }
}

suspend fun Backend.exitCommunity(
    communityId: PrimaryKey,
    id: PrimaryKey
) = getCommunity(ObjectFetch.IdFetch(communityId), id, true).mapResultIfNotNull { info ->
    if (info.joinedTime == null) {
        Result.success(info)
    } else {
        database.container.exitContainer(communityId, id).mapResult {
            addUserLog(id, UserLogType.EXIT, communityId ob ObjectType.COMMUNITY)
            Result.success(info.copy(joinedTime = null))
        }
    }
}

suspend fun Backend.searchCommunities(
    uid: PrimaryKey?,
    search: CustomApi.Communities.CommunitySearchQuery,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<CommunityInfo>?> {
    val word = search.word
    val joinSearch = if (search.target != null) {
        JoinStatusSearch.JOINED.toJoinSearch(search.target)
    } else {
        search.joinStatus.toJoinSearch(uid)
    }
    return if (word.isNullOrBlank() || joinSearch !is JoinSearch.Unspecified) {
        database.community.getCommunityPaginationResult(
            word,
            search.hasPoster,
            primaryKeyFetch,
            joinSearch
        )
    } else {
        communitySearchService.searchDocument(
            CommunityDocumentSearch.Keyword(listOf(word)),
            primaryKeyFetch
        ).mapResult { (list, total) ->
            database.community.getRawCommunities(
                ObjectListFetch.IdListFetch(list.map {
                    it.id
                })
            ).map {
                PaginationResult(it, total)
            }
        }
    }.mapResultIfNotNull { (list, count) ->
        processRawCommunityToCommunityInfo(list).mapResultIfNotNull { value ->
            when {
                search.target == null -> Result.success(PaginationResult(value, count))
                uid != null -> processUserJoinedTimeReplace(value, uid, count)
                else -> Result.success(PaginationResult(value.map {
                    it.copy(joinedTime = null, extension = CommunityInfo.Extension(it.joinedTime))
                }, count))
            }
        }
    }
}

private suspend fun Backend.processUserJoinedTimeReplace(
    value: List<CommunityInfo>,
    uid: PrimaryKey,
    count: Long
): Result<PaginationResult<CommunityInfo>> {
    val communityIds = value.map {
        it.id
    }
    return database.community.getCommunityJoinedTimeByIds(uid, communityIds)
        .map { joinedTimeList ->
            val map = joinedTimeList.associate { it }
            PaginationResult(value.map {
                it.copy(joinedTime = map[it.id], extension = CommunityInfo.Extension(it.joinedTime))
            }, count)
        }
}

suspend fun Backend.createCommunity(
    newCommunity: NewCommunity,
    uid: PrimaryKey
): Result<CommunityInfo?> {
    return runCatching {
        checkAid(newCommunity.aid).getOrThrow()
        checkCommunityName(newCommunity).getOrThrow()
    }.mapResult {
        val id = SnowflakeFactory.nextId()
        val memberId = SnowflakeFactory.nextId()
        val community = Community(
            id,
            now(),
            newCommunity.aid,
            newCommunity.name,
            uid,
            newCommunity.memberPolicy,
            newCommunity.icon,
            null
        )
        database.community.createCommunity(community, memberId).onSuccess {
            communitySearchService.saveDocument(listOf(CommunityDocument.fromCommunity(community)))
                .onFailure {
                    Napier.e(it) {
                        "save community document failed"
                    }
                }
        }
    }.mapResult { community ->
        addUserLog(uid, UserLogType.CREATE, community.id ob ObjectType.COMMUNITY)
        processRawCommunityToCommunityInfo(
            listOf(
                RawCommunity(
                    community,
                    community.createdTime,
                    null,
                    0
                )
            )
        ).mapIfNotNull {
            it.first()
        }
    }
}

private fun checkCommunityName(newCommunity: NewCommunity): Result<Unit> {
    return when (checkNickname(newCommunity.name, 1..COMMUNITY_NAME_LENGTH)) {
        StringCheckResult.RANGE_MISMATCH -> Result.failure(
            CustomBadRequestException("community name must be between in 1 and $COMMUNITY_NAME_LENGTH")
        )

        StringCheckResult.CONTAIN_INVALID_CHAR -> Result.failure(
            CustomBadRequestException("community name must be visible")
        )

        else -> Result.failure(CustomBadRequestException("name must be set"))
    }
}

suspend fun Backend.updateCommunity(
    id: PrimaryKey,
    old: UpdateCommunityBody,
    uid: PrimaryKey
): Result<CommunityInfo?> {
    val newCommunity = old.copy(name = old.name?.trim(), icon = old.icon, poster = old.poster)
    return checkRootAdminPermission(ObjectType.COMMUNITY, id, uid).mapResultIfNotNull {
        checkBeforeUpdateCommunity(newCommunity)
    }.mapResultIfNotNull {
        database.community.updateCommunity(id, newCommunity).errorIfFalse {
            CustomBadRequestException("update failed")
        }
    }.mapResultIfNotNull {
        database.community.getRawCommunity(ObjectFetch.IdFetch(id), true, uid)
            .mapResultIfNotNull { rawResult ->
                processRawCommunityToCommunityInfo(
                    listOf(rawResult)
                ).mapIfNotNull(List<CommunityInfo>::first)
            }
    }
}

private suspend fun Backend.checkBeforeUpdateCommunity(
    newCommunity: UpdateCommunityBody,
): Result<Unit> {
    runCatching {
        checkCommunityNameForUpdate(newCommunity).getOrThrow()
        checkCommunityIconForUpdate(newCommunity).getOrThrow()
        checkCommunityPosterForUpdate(newCommunity).getOrThrow()
    }.exceptionOrNull()?.let {
        return Result.failure(it)
    }
    return UNIT_RESULT
}

private suspend fun Backend.checkCommunityPosterForUpdate(newCommunity: UpdateCommunityBody): Result<Unit> =
    checkIcon(newCommunity.poster, Dimension.COMMUNITY_POSTER).mapResult { checkResult ->
        when (checkResult) {
            MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("poster not found"))
            MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                CustomBadRequestException("only support image")
            )

            MediaCheckResult.DIMENSION_MISMATCH -> Result.failure(
                CustomBadRequestException("dimension mismatch")
            )

            else -> UNIT_RESULT
        }
    }

private suspend fun Backend.checkCommunityIconForUpdate(newCommunity: UpdateCommunityBody): Result<Unit> =
    checkIcon(newCommunity.icon, Dimension.DEFAULT_DIMENSION).mapResult { checkResult ->
        when (checkResult) {
            MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("icon not found"))
            MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                CustomBadRequestException("only support image")
            )

            MediaCheckResult.DIMENSION_MISMATCH -> Result.failure(
                CustomBadRequestException("dimension mismatch")
            )

            else -> UNIT_RESULT
        }
    }

private fun checkCommunityNameForUpdate(newCommunity: UpdateCommunityBody): Result<Unit> =
    when (checkNickname(newCommunity.name, 1..COMMUNITY_NAME_LENGTH)) {
        StringCheckResult.RANGE_MISMATCH -> Result.failure(
            CustomBadRequestException("community name must be between in 1 and 20")
        )

        StringCheckResult.CONTAIN_INVALID_CHAR -> Result.failure(
            CustomBadRequestException("community name must be visible")
        )

        else -> UNIT_RESULT
    }

suspend fun Backend.processRawCommunityToCommunityInfo(
    list: List<RawCommunity>,
): Result<List<CommunityInfo>?> {
    return database.file.getFileRecordByIds(list.flatMap { (community) ->
        listOf(community.iconId, community.posterId, community.fontId)
    }.filterNotNull()).mapResultIfNotNull { medias ->
        processFileRecordToFileInfo(medias).map { mediaList ->
            val map = mediaList.associateBy { it.id }
            list.map { rawResult ->
                rawResult.toCommunityIfo(
                    rawResult.community.iconId?.let { map[it] },
                    rawResult.community.posterId?.let { map[it] },
                    rawResult.community.fontId?.let { map[it] }
                )
            }
        }
    }
}

fun JoinStatusSearch?.toJoinSearch(uid: PrimaryKey?): JoinSearch {
    if (this != JoinStatusSearch.JOINED) return JoinSearch.Unspecified(uid)
    if (uid == null) throw UnauthorizedException()
    return JoinSearch.Joined(uid)
}
