package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.a.server.route.RouteCommunities
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.COMMUNITY_NAME_LENGTH
import com.storyteller_f.backend.service.CustomBadRequestException
import com.storyteller_f.backend.service.ObjectFetch
import com.storyteller_f.backend.service.isDup
import com.storyteller_f.backend.service.processCommunityRawResultToCommunityInfo
import com.storyteller_f.backend.service.query.addCommunityJoin
import com.storyteller_f.backend.service.query.createCommunity
import com.storyteller_f.backend.service.query.exit
import com.storyteller_f.backend.service.query.getCommunityJoinedTimeByIds
import com.storyteller_f.backend.service.query.getCommunityPaginationResult
import com.storyteller_f.backend.service.query.getCommunityRawResult
import com.storyteller_f.backend.service.query.updateCommunity
import com.storyteller_f.backend.service.tables.Community
import com.storyteller_f.backend.service.tables.CommunityRawResult
import com.storyteller_f.backend.service.tables.toCommunityIfo
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.ktor.server.plugins.*

suspend fun Backend.getCommunity(
    objectFetch: ObjectFetch,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return this.databaseSession.getCommunityRawResult(
        objectFetch,
        fillJoinInfo,
        id
    ).mapResultIfNotNull {
        processCommunityRawResultToCommunityInfo(listOf(it)).mapIfNotNull(List<CommunityInfo>::first)
    }
}

suspend fun Backend.doUserJoinCommunity(
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
        val time = now()
        this.databaseSession.addCommunityJoin(uid, communityId, time).mapResult {
            addUserLog(uid, UserLogType.JOIN, communityId ob ObjectType.COMMUNITY)
            Result.success(community.copy(joinedTime = time))
        }.recoverCatching {
            if (it.isDup()) {
                getCommunity(ObjectFetch.IdFetch(communityId), uid, true)
            } else {
                Result.failure(it)
            }
        }
    }
}

suspend fun Backend.exitCommunity(
    communityId: PrimaryKey,
    id: PrimaryKey
) =
    getCommunity(ObjectFetch.IdFetch(communityId), id, true).mapResultIfNotNull { info ->
        if (info.joinedTime == null) {
            Result.success(info)
        } else {
            this.databaseSession.exit(communityId, id).mapResult { i ->
                if (i > 0) {
                    addUserLog(id, UserLogType.EXIT, communityId ob ObjectType.COMMUNITY)
                    Result.success(info.copy(joinedTime = null))
                } else {
                    Result.failure(BadRequestException("exit failed"))
                }
            }
        }
    }

suspend fun Backend.searchCommunities(
    uid: PrimaryKey?,
    search: RouteCommunities.Search,
    primaryKeyFetch: PrimaryKeyFetch
) = this.databaseSession.getCommunityPaginationResult(
    search.target ?: uid,
    if (search.target != null) JoinStatusSearch.JOINED else search.joinStatus,
    search.word,
    search.hasPoster,
    primaryKeyFetch
).mapResultIfNotNull { (list, count) ->
    processCommunityRawResultToCommunityInfo(list).mapResultIfNotNull { value ->
        when {
            search.target == null -> Result.success(PaginationResult(value, count))
            uid != null -> processUserJoinedTimeReplace(value, uid, count)
            else -> Result.success(PaginationResult(value.map {
                it.copy(joinedTime = null, extension = CommunityInfo.Extension(it.joinedTime))
            }, count))
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
    return this.databaseSession.getCommunityJoinedTimeByIds(uid, communityIds).map { joinedTimeList ->
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
        newCommunity.icon,
        null
    )
    return this.databaseSession.createCommunity(community).mapResult {
        val communityInfo = community
        addUserLog(uid, UserLogType.CREATE, communityInfo.toCommunityIfo().tuple())
        processCommunityRawResultToCommunityInfo(
            listOf(
                CommunityRawResult(
                    communityInfo,
                    newCommunity.icon,
                    null,
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

suspend fun Backend.updateCommunity(
    id: PrimaryKey,
    old: UpdateCommunityBody,
    uid: PrimaryKey
): Result<CommunityInfo?> {
    val newCommunity = old.copy(name = old.name?.trim(), icon = old.icon?.trim(), poster = old.poster?.trim())
    return checkRootAdminPermission(ObjectType.COMMUNITY, id, uid).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            checkBeforeUpdateCommunity(newCommunity).mapResult {
                this.databaseSession.updateCommunity(id, newCommunity).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        this.databaseSession.getCommunityRawResult(
                            ObjectFetch.IdFetch(id),
                            true,
                            uid
                        ).mapResultIfNotNull { rawResult ->
                            processCommunityRawResultToCommunityInfo(
                                listOf(rawResult)
                            ).mapIfNotNull(List<CommunityInfo>::first)
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

private suspend fun Backend.checkBeforeUpdateCommunity(
    newCommunity: UpdateCommunityBody,
): Result<Unit> {
    val firstError = listOf(suspend {
        when (checkNickname(newCommunity.name, 1..COMMUNITY_NAME_LENGTH)) {
            StringCheckResult.RANGE_MISMATCH -> Result.failure(
                CustomBadRequestException("community name must be between in 1 and 20")
            )

            else -> Result.success(Unit)
        }
    }, suspend {
        checkIcon(newCommunity.icon, Dimension(1, 1)).mapResult { checkResult ->
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
        checkIcon(newCommunity.poster, Dimension(3, 4)).mapResult { checkResult ->
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
