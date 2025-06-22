package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.exposed.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.exposed.isDup
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.a.exposed.tables.Community
import com.storyteller_f.a.exposed.tables.CommunityRawResult
import com.storyteller_f.a.exposed.tables.toCommunityIfo
import com.storyteller_f.a.exposed.toJoinSearch
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.backend.service.*
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
import com.storyteller_f.shared.utils.recoverResult
import io.ktor.server.plugins.*

suspend fun Backend.getCommunity(
    objectFetch: ObjectFetch,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return exposedDatabase.communityDatabase.getCommunityRawResult(
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
        exposedDatabase.userDatabase.addCommunityJoin(uid, communityId, time).mapResult {
            addUserLog(uid, UserLogType.JOIN, communityId ob ObjectType.COMMUNITY)
            Result.success(community.copy(joinedTime = time))
        }.recoverResult {
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
            exposedDatabase.userDatabase.exit(communityId, id).mapResult { i ->
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
    search: CustomApi.Communities.CommunitySearchQuery,
    primaryKeyFetch: PrimaryKeyFetch
) = exposedDatabase.communityDatabase.getCommunityPaginationResult(
    search.word,
    search.hasPoster,
    primaryKeyFetch,
    (if (search.target != null) {
        JoinStatusSearch.JOINED.toJoinSearch(search.target)
    } else {
        search.joinStatus.toJoinSearch(uid)
    })
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
    return exposedDatabase.communityDatabase.getCommunityJoinedTimeByIds(uid, communityIds).map { joinedTimeList ->
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
    return exposedDatabase.communityDatabase.createCommunity(community).mapResult {
        databaseSession.dbQuery {
            createCommunityRoomsRaw(community.id, community.owner, community.aid)
        }

        val communityInfo = community
        addUserLog(uid, UserLogType.CREATE, communityInfo.toCommunityIfo().tuple())
        processCommunityRawResultToCommunityInfo(
            listOf(
                CommunityRawResult(
                    communityInfo,
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
    val newCommunity = old.copy(name = old.name?.trim(), icon = old.icon, poster = old.poster)
    return checkRootAdminPermission(ObjectType.COMMUNITY, id, uid).mapResultIfNotNull { permission ->
        if (permission.hasAdmin) {
            checkBeforeUpdateCommunity(newCommunity).mapResult {
                exposedDatabase.communityDatabase.updateCommunity(id, newCommunity).mapResult { updateSuccess ->
                    if (updateSuccess) {
                        exposedDatabase.communityDatabase.getCommunityRawResult(
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
