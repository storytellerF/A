package com.storyteller_f.a.server.service

import com.storyteller_f.*
import com.storyteller_f.a.server.route.RouteCommunities
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.types.PaginationResult
import io.ktor.server.plugins.BadRequestException

suspend fun getCommunity(
    communityId: PrimaryKey?,
    communityAid: String?,
    backend: Backend,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return getCommonCommunity(
        fillJoinInfo,
        communityId,
        communityAid,
        id
    ).mapResultNotNull { (info, iconName, coverName) ->
        backend.mediaService.get("amedia", listOf(iconName, coverName)).map { (iconInfo, coverInfo) ->
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
        addCommunityJoin(uid, communityId, time).mapResult { value ->
            if (value > 0) {
                Result.success(community.copy(joinTime = time))
            } else {
                Result.failure(BadRequestException("join failed."))
            }
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
            exit(communityId, id).mapResult { i ->
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
    return commonPaginationCommunityList(
        uid,
        prePageToken,
        nextPageToken,
        size,
        search.joinStatus,
        search.word
    ).mapResult { (list, count) ->
        parseCommunityList(backend, list).map { value ->
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
            parseCommunityList(backend, list).mapResult { value ->
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
    return getCommunityByIds(uid, communityIds).map { joinedTimeList ->
        val map = joinedTimeList.associate { it }
        PaginationResult(value.map {
            it.copy(joinTime = map[it.id], extension = CommunityInfo.Extension(it.joinTime))
        }, count)
    }
}

private fun parseCommunityList(
    backend: Backend,
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>> {
    return backend.mediaService.get("amedia", list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }).map { icons ->
        list.mapIndexed { i, communityPair ->
            val first = icons[i * 2]
            val second = icons[i * 2 + 1]
            communityPair.communityInfo.copy(icon = first, poster = second)
        }
    }
}

suspend fun getCommunityTopicList(
    it: RouteCommunities.Id.Topics,
    id: PrimaryKey?,
    n: PrimaryKey?,
    s: Int,
    backend: Backend,
    search: RouteCommunities.Id.Topics
) = checkRootReadPermission(ObjectType.COMMUNITY, it.parent.id, id).mapResultNotNull {
    if (it.hasRead) {
        commonPaginationTopics(id, null, n, s, search.fillHasCommented) {
            Topics.rootId eq search.parent.id
        }.mapResultNotNull { (data, count) ->
            val ids = data.map {
                it.id
            }
            backend.topicSearchService.getDocument(ids).mapResult { list ->
                processMediaLink(backend, data, list).map {
                    PaginationResult(it, count)
                }
            }
        }
    } else {
        Result.failure(ForbiddenException())
    }
}
