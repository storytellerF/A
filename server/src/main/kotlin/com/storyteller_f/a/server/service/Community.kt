package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.a.server.route.RouteCommunities
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.CommunityInfo
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

suspend fun joinCommunity(
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
