package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.a.server.route.RouteCommunities
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.TopicPinSearch
import com.storyteller_f.shared.obj.TopicPinSearch.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.*
import org.jetbrains.exposed.sql.and

suspend fun getCommunity(
    communityId: PrimaryKey?,
    communityAid: String?,
    backend: Backend,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return DatabaseFactory.getCommonCommunity(
        communityId,
        communityAid,
        fillJoinInfo,
        id
    ).mapResultNotNull { (info, iconName, coverName) ->
        backend.mediaService.get(AMEDIA_BUCKET, listOf(iconName, coverName)).map { (iconInfo, coverInfo) ->
            info.copy(icon = iconInfo, poster = coverInfo)
        }
    }
}

suspend fun doUserJoinCommunity(
    uid: PrimaryKey,
    communityId: PrimaryKey,
    backend: Backend
) = getCommunity(communityId, null, backend, uid, true).mapResultNotNull { community ->
    if (community.joinTime != null) {
        Result.success(community)
    } else {
        val time = now()
        DatabaseFactory.addCommunityJoin(uid, communityId, time).mapResult { value ->
            Result.success(community.copy(joinTime = time))
        }.recoverCatching {
            if (it.isDup()) {
                getCommunity(communityId, null, backend, uid, true)
            } else {
                Result.failure(it)
            }
        }
    }
}

suspend fun exitCommunity(communityId: PrimaryKey, id: PrimaryKey, backend: Backend) =
    getCommunity(communityId, null, backend, id, true).mapResultNotNull { info ->
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
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    uid: PrimaryKey?,
    search: RouteCommunities.Search
): Result<PaginationResult<CommunityInfo>?> {
    if (search.joinStatus == JoinStatusSearch.JOINED && search.target != null) {
        return searchTargetUserJoinedCommunities(prePageToken, nextPageToken, size, backend, uid, search.target)
    }
    return DatabaseFactory.getPaginationCommunityList(
        uid,
        prePageToken,
        nextPageToken,
        size,
        search.joinStatus,
        search.word
    ).mapResult { (list, count) ->
        processCommunityList(backend, list).map { value ->
            PaginationResult(value, count)
        }
    }
}

private suspend fun searchTargetUserJoinedCommunities(
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    backend: Backend,
    uid: PrimaryKey?,
    target: PrimaryKey
): Result<PaginationResult<CommunityInfo>> {
    return DatabaseFactory.mapQuery({
        CommunityRawResult(first.toCommunityIfo(second), first.icon, first.poster)
    }, {
        Community.wrapRow(it) to it[MemberJoins.joinTime]
    }) {
        getUserJoinedCommunityQuery(target, false).bindPaginationQuery(
            Communities,
            prePageToken,
            nextPageToken,
            size
        )
    }.mapResult { list ->
        DatabaseFactory.count {
            getUserJoinedCommunityQuery(target, true)
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
    return DatabaseFactory.getJoinedCommunityByIds(uid, communityIds).map { joinedTimeList ->
        val map = joinedTimeList.associate { it }
        PaginationResult(value.map {
            it.copy(joinTime = map[it.id], extension = CommunityInfo.Extension(it.joinTime))
        }, count)
    }
}

suspend fun getCommunityTopicList(
    id: PrimaryKey?,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int,
    backend: Backend,
    communityId: PrimaryKey,
    fillHasCommented: Boolean?,
    pinType: TopicPinSearch?
): Result<PaginationResult<TopicInfo>?> {
    return checkRootReadPermission(
        ObjectType.COMMUNITY,
        communityId, id
    ).mapResultNotNull { permission ->
        if (permission.hasRead) {
            getTopicsPagingByPredicate(id, preTopicId, nextTopicId, size, fillHasCommented) {
                val baseQuery = Topics.parentId eq communityId
                when(pinType) {
                    PINNED -> baseQuery and (Topics.pinned eq true)
                    UNPINNED -> baseQuery and (Topics.pinned eq false)
                    else -> baseQuery
                }
            }.mapResultNotNull { (data, count) ->
                val ids = data.map {
                    it.id
                }
                backend.topicSearchService.getDocuments(ids).mapResult { list ->
                    processMediaAndAuthor(backend, data, list).map {
                        PaginationResult(it, count)
                    }
                }
            }
        } else {
            Result.failure(ForbiddenException())
        }
    }
}

private fun checkCommunityName(newCommunity: NewCommunity): Result<Unit> {
    val nickname = newCommunity.name
    return when {
        nickname.isEmpty() -> Result.failure(CustomBadRequestException("community name is empty"))
        nickname.length in 1..COMMUNITY_NAME_LENGTH -> Result.success(Unit)
        else -> Result.failure(CustomBadRequestException("community name must be between in 1 and 20"))
    }
}

suspend fun createCommunity(newCommunity: NewCommunity, uid: PrimaryKey, backend: Backend): Result<CommunityInfo> {
    val firstError = listOf(suspend {
        checkAid(newCommunity.aid)
    }, suspend {
        checkCommunityName(newCommunity)
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
    return DatabaseFactory.createCommunity(community).mapResult {
        processCommunityList(
            backend,
            listOf(CommunityRawResult(community.toCommunityIfo(community.createdTime), newCommunity.icon, null))
        ).map {
            it.first()
        }
    }
}
