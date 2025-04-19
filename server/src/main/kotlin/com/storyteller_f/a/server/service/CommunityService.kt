package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.route.RouteCommunities
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.*

suspend fun getCommunity(
    objectFetch: ObjectFetch,
    backend: Backend,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return DatabaseFactory.getCommunity(
        objectFetch,
        fillJoinInfo,
        id
    ).mapResultNotNull {
        processCommunityList(backend, listOf(it)).map(List<CommunityInfo>::first)
    }
}

suspend fun doUserJoinCommunity(
    uid: PrimaryKey,
    communityId: PrimaryKey,
    backend: Backend
) = getCommunity(ObjectFetch.IdFetch(communityId), backend, uid, true).mapResultNotNull { community ->
    if (community.joinTime != null) {
        Result.success(community)
    } else {
        val time = now()
        DatabaseFactory.addCommunityJoin(uid, communityId, time, community.memberCount).mapResult {
            Result.success(community.copy(joinTime = time))
        }.recoverCatching {
            if (it.isDup()) {
                getCommunity(ObjectFetch.IdFetch(communityId), backend, uid, true)
            } else {
                Result.failure(it)
            }
        }
    }
}

suspend fun exitCommunity(communityId: PrimaryKey, id: PrimaryKey, backend: Backend) =
    getCommunity(ObjectFetch.IdFetch(communityId), backend, id, true).mapResultNotNull { info ->
        if (info.joinTime == null) {
            Result.success(info)
        } else {
            DatabaseFactory.exit(communityId, id).mapResult { i ->
                if (i > 0) {
                    Result.success(info.copy(joinTime = null))
                } else {
                    Result.failure(BadRequestException("exit failed"))
                }
            }
        }
    }

suspend fun searchCommunities(
    backend: Backend,
    uid: PrimaryKey?,
    search: RouteCommunities.Search,
    pagingFetch: PagingFetch
): Result<PaginationResult<CommunityInfo>?> {
    if (search.joinStatus == JoinStatusSearch.JOINED && search.target != null) {
        return searchTargetUserJoinedCommunities(
            uid,
            backend,
            search.target,
            search.hasPoster,
            pagingFetch
        )
    }
    return DatabaseFactory.getPaginationCommunityList(
        uid,
        search.joinStatus,
        search.word,
        search.hasPoster,
        pagingFetch
    ).mapResult { (list, count) ->
        processCommunityList(backend, list).map { value ->
            PaginationResult(value, count)
        }
    }
}

private suspend fun searchTargetUserJoinedCommunities(
    uid: PrimaryKey?,
    backend: Backend,
    target: PrimaryKey,
    hasPosterSearch: PosterSearch?,
    pagingFetch: PagingFetch
): Result<PaginationResult<CommunityInfo>> {
    return DatabaseFactory.mapQuery({
        CommunityRawResult(first.toCommunityIfo(second), first.icon, first.poster)
    }, {
        Community.wrapRow(it) to it[MemberJoins.joinTime]
    }) {
        getUserJoinedCommunityQuery(target, false).bindPosterSearch(hasPosterSearch).bindPaginationQuery(
            Communities,
            pagingFetch
        )
    }.mapResult { list ->
        DatabaseFactory.count {
            getUserJoinedCommunityQuery(target, true).bindPosterSearch(hasPosterSearch)
        }.mapResult { count ->
            processCommunityList(backend, list).mapResult { value ->
                if (uid != null) {
                    processUserJoinedTimeReplace(value, uid, count)
                } else {
                    Result.success(PaginationResult(value.map {
                        it.copy(joinTime = null, extension = CommunityInfo.Extension(it.joinTime))
                    }, count))
                }
            }
        }
    }
}

private suspend fun processUserJoinedTimeReplace(
    value: List<CommunityInfo>,
    uid: PrimaryKey,
    count: Long
): Result<PaginationResult<CommunityInfo>> {
    val communityIds = value.map {
        it.id
    }
    return DatabaseFactory.getCommunityJoinedTimeByIds(uid, communityIds).map { joinedTimeList ->
        val map = joinedTimeList.associate { it }
        PaginationResult(value.map {
            it.copy(joinTime = map[it.id], extension = CommunityInfo.Extension(it.joinTime))
        }, count)
    }
}

suspend fun createCommunity(newCommunity: NewCommunity, uid: PrimaryKey, backend: Backend): Result<CommunityInfo> {
    val firstError = listOf(suspend {
        checkAid(newCommunity.aid)
    }, suspend {
        when (checkNickname(newCommunity.name, 1..COMMUNITY_NAME_LENGTH)) {
            StringCheckResult.RANGE_MISMATCH -> Result.failure(
                CustomBadRequestException("community name must be between in 1 and 20")
            )

            StringCheckResult.EMPTY -> Result.failure(CustomBadRequestException("community name is empty"))
            StringCheckResult.SUCCESS -> Result.success(Unit)
        }
    }).firstNotNullOfOrNull {
        it().exceptionOrNull()
    }
    if (firstError != null) return Result.failure(firstError)
    val id = SnowflakeFactory.nextId()
    val community = Community(
        id,
        now(),
        newCommunity.aid,
        newCommunity.name,
        uid,
        0,
        newCommunity.icon,
        null
    )
    return DatabaseFactory.createCommunity(community).mapResult {
        processCommunityList(
            backend,
            listOf(CommunityRawResult(community.toCommunityIfo(community.createdTime), newCommunity.icon, null))
        ).map {
            it.first()
        }
    }
}

suspend fun updateCommunity(
    id: PrimaryKey,
    backend: Backend,
    old: UpdateCommunityBody,
    uid: PrimaryKey
): Result<CommunityInfo?> {
    val newCommunity = old.copy(name = old.name?.trim(), icon = old.icon?.trim(), poster = old.poster?.trim())
    return checkRootAdminPermission(ObjectType.COMMUNITY, id, uid).mapResultNotNull {
        if (it.hasAdmin) {
            val firstError = listOf(suspend {
                when (checkNickname(newCommunity.name, 1..COMMUNITY_NAME_LENGTH)) {
                    StringCheckResult.RANGE_MISMATCH -> Result.failure(
                        CustomBadRequestException("community name must be between in 1 and 20")
                    )

                    else -> Result.success(Unit)
                }
            }, suspend {
                checkIcon(backend, newCommunity.icon, Dimension(1, 1)).mapResult { checkResult ->
                    when (checkResult) {
                        MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("icon not found"))
                        MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                            CustomBadRequestException("only support image")
                        )

                        MediaCheckResult.DIMENSION_MISMATCH -> Result.failure(
                            CustomBadRequestException("dimension mismatch")
                        )

                        else -> Result.success(Unit)
                    }
                }
            }, suspend {
                checkIcon(backend, newCommunity.poster, Dimension(3, 4)).mapResult { checkResult ->
                    when (checkResult) {
                        MediaCheckResult.NOT_FOUND -> Result.failure(CustomBadRequestException("poster not found"))
                        MediaCheckResult.CONTENT_TYPE_MISMATCH -> Result.failure(
                            CustomBadRequestException("only support image")
                        )

                        MediaCheckResult.DIMENSION_MISMATCH -> Result.failure(
                            CustomBadRequestException("dimension mismatch")
                        )

                        else -> Result.success(Unit)
                    }
                }
            }).firstNotNullOfOrNull {
                it().exceptionOrNull()
            }
            if (firstError != null) {
                Result.failure(firstError)
            } else {
                DatabaseFactory.updateCommunity(id, newCommunity).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        DatabaseFactory.getCommunity(ObjectFetch.IdFetch(id), true, uid).mapResultNotNull { rawResult ->
                            processCommunityList(backend, listOf(rawResult)).map(List<CommunityInfo>::first)
                        }
                    } else {
                        Result.success(null)
                    }
                }
            }
        } else {
            Result.failure(CustomBadRequestException("forbid"))
        }
    }
}
