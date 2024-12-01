package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.UnauthorizedException
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.Communities
import com.storyteller_f.tables.Community
import com.storyteller_f.tables.MemberJoins
import com.storyteller_f.types.PaginationResult
import org.jetbrains.exposed.sql.*

suspend fun searchCommunities(
    backend: Backend,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    uid: PrimaryKey?,
    search: RouteCommunities.Search
): Result<PaginationResult<CommunityInfo>?> {
    return DatabaseFactory.mapQuery({
        Triple(toCommunityIfo(null), icon, poster)
    }, Community::wrapRow) {
        getSearchCommunityQuery(search, uid, false).bindPaginationQuery(Communities, prePageToken, nextPageToken, size)
    }.mapResult { list ->
        DatabaseFactory.count {
            getSearchCommunityQuery(search, uid, true)
        }.mapResult { count ->
            parseCommunityList(backend, list).map { value ->
                PaginationResult(value, count)
            }
        }
    }
}

private fun getSearchCommunityQuery(
    search: RouteCommunities.Search,
    uid: PrimaryKey?,
    getCount: Boolean
): Query {
    val joinStatusSearch = search.joinStatus
    val word = search.word
    val query = when (joinStatusSearch) {
        JoinStatusSearch.JOINED -> {
            if (uid != null) {
                val join = Communities.join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
                    MemberJoins.uid eq uid
                }
                if (getCount) {
                    join.selectAll()
                } else {
                    join.select(Communities.fields + MemberJoins.joinTime)
                }
            } else {
                throw UnauthorizedException()
            }
        }

        JoinStatusSearch.NOT_JOINED -> {
            if (uid != null) {
                val join = Communities
                join.selectAll().where {
                    Communities.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
                        MemberJoins.uid eq uid
                    })
                }
            } else {
                throw UnauthorizedException()
            }
        }

        else -> {
            Communities.selectAll()
        }
    }
    if (!(word.isNullOrBlank())) {
        query.andWhere {
            Communities.name like "%$word%"
        }
    }
    return query
}

private fun parseCommunityList(
    backend: Backend,
    list: List<Triple<CommunityInfo, String?, String?>>
): Result<List<CommunityInfo>> {
    return backend.mediaService.get("apic", list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }).map { icons ->
        list.mapIndexed { i, communityPair ->
            val first = icons[i * 2]
            val second = icons[i * 2 + 1]
            communityPair.first.copy(icon = getMediaInfo(first), poster = getMediaInfo(second))
        }
    }
}
