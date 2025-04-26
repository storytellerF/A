package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.a.server.route.RouteCommunities
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import io.ktor.server.plugins.*

suspend fun getCommunity(
    backend: Backend,
    objectFetch: ObjectFetch,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return DatabaseFactory.getCommunity(
        backend,
        objectFetch,
        fillJoinInfo,
        id
    ).mapResultIfNotNull {
        processCommunityList(backend, listOf(it)).map(List<CommunityInfo>::first)
    }
}

suspend fun doUserJoinCommunity(
    backend: Backend,
    uid: PrimaryKey,
    communityId: PrimaryKey
) = getCommunity(
    backend,
    ObjectFetch.IdFetch(communityId),
    uid,
    true
).mapResultIfNotNull { community ->
    if (community.joinTime != null) {
        Result.success(community)
    } else {
        val time = now()
        DatabaseFactory.addCommunityJoin(backend, uid, communityId, time, community.memberCount).mapResult {
            addUserLog(backend, uid, UserLogType.JOIN, communityId ob ObjectType.COMMUNITY)
            Result.success(community.copy(joinTime = time))
        }.recoverCatching {
            if (it.isDup()) {
                getCommunity(backend, ObjectFetch.IdFetch(communityId), uid, true)
            } else {
                Result.failure(it)
            }
        }
    }
}

suspend fun exitCommunity(
    backend: Backend,
    communityId: PrimaryKey,
    id: PrimaryKey
) =
    getCommunity(backend, ObjectFetch.IdFetch(communityId), id, true).mapResultIfNotNull { info ->
        if (info.joinTime == null) {
            Result.success(info)
        } else {
            DatabaseFactory.exit(backend, communityId, id).mapResult { i ->
                if (i > 0) {
                    addUserLog(backend, id, UserLogType.EXIT, communityId ob ObjectType.COMMUNITY)
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
            backend,
            uid,
            search.target,
            search.hasPoster,
            pagingFetch
        )
    }
    return DatabaseFactory.getPaginationCommunityList(
        backend,
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
    backend: Backend,
    uid: PrimaryKey?,
    target: PrimaryKey,
    hasPosterSearch: PosterSearch?,
    pagingFetch: PagingFetch
): Result<PaginationResult<CommunityInfo>> {
    return DatabaseFactory.mapQuery(backend, {
        CommunityRawResult(first.toCommunityIfo(second), first.icon, first.poster)
    }, {
        Community.wrapRow(it) to it[MemberJoins.joinTime]
    }) {
        getUserJoinedCommunityQuery(target, false).bindPosterSearch(hasPosterSearch).bindPaginationQuery(
            Communities,
            pagingFetch
        )
    }.mapResult { list ->
        DatabaseFactory.count(backend) {
            getUserJoinedCommunityQuery(target, true).bindPosterSearch(hasPosterSearch)
        }.mapResult { count ->
            processCommunityList(backend, list).mapResult { value ->
                if (uid != null) {
                    processUserJoinedTimeReplace(backend, value, uid, count)
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
    backend: Backend,
    value: List<CommunityInfo>,
    uid: PrimaryKey,
    count: Long
): Result<PaginationResult<CommunityInfo>> {
    val communityIds = value.map {
        it.id
    }
    return DatabaseFactory.getCommunityJoinedTimeByIds(backend, uid, communityIds).map { joinedTimeList ->
        val map = joinedTimeList.associate { it }
        PaginationResult(value.map {
            it.copy(joinTime = map[it.id], extension = CommunityInfo.Extension(it.joinTime))
        }, count)
    }
}

suspend fun createCommunity(
    backend: Backend,
    newCommunity: NewCommunity,
    uid: PrimaryKey
): Result<CommunityInfo> {
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
    return DatabaseFactory.createCommunity(backend, community).mapResult {
        val communityInfo = community.toCommunityIfo(community.createdTime)
        addUserLog(backend, uid, UserLogType.CREATE, communityInfo.tuple())
        processCommunityList(
            backend,
            listOf(CommunityRawResult(communityInfo, newCommunity.icon, null))
        ).map {
            it.first()
        }
    }
}

suspend fun updateCommunity(
    backend: Backend,
    id: PrimaryKey,
    old: UpdateCommunityBody,
    uid: PrimaryKey
): Result<CommunityInfo?> {
    val newCommunity = old.copy(name = old.name?.trim(), icon = old.icon?.trim(), poster = old.poster?.trim())
    return checkRootAdminPermission(backend, ObjectType.COMMUNITY, id, uid).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            checkBeforeUpdateCommunity(newCommunity, backend).mapResult {
                DatabaseFactory.updateCommunity(backend, id, newCommunity).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        DatabaseFactory.getCommunity(
                            backend,
                            ObjectFetch.IdFetch(id),
                            true,
                            uid
                        ).mapResultIfNotNull { rawResult ->
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

private suspend fun checkBeforeUpdateCommunity(
    newCommunity: UpdateCommunityBody,
    backend: Backend,
): Result<Unit> {
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
    return if (firstError != null) {
        Result.failure(firstError)
    } else {
        Result.success(Unit)
    }
}
