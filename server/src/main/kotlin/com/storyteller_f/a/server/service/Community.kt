package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.selectAll

fun Community.toCommunityIfo(
    joinTime: LocalDateTime?
): CommunityInfo = CommunityInfo(
    id,
    aid,
    name,
    owner,
    createdTime,
    null,
    null,
    joinTime
)

suspend fun getCommunity(communityId: PrimaryKey, backend: Backend): Result<CommunityInfo?> {
    return runCatching {
        getCommunityInternal(backend) {
            Community.findById(communityId)
        }
    }
}

suspend fun getCommunityByAid(communityAid: String, backend: Backend): Result<CommunityInfo?> {
    return runCatching {
        getCommunityInternal(backend) {
            Community.find {
                Communities.aid eq communityAid
            }
        }
    }
}

private suspend fun getCommunityInternal(backend: Backend, searchCommunity: suspend () -> SizedIterable<ResultRow>) =
    DatabaseFactory.first({
        Triple(toCommunityIfo(now()), icon, poster)
    }, {
        Community.wrapRow(it)
    }, searchCommunity)?.let { (info, iconName, coverName) ->
        val (iconUrl, coverUrl) = backend.mediaService.get("apic", listOf(iconName, coverName))
        info.copy(icon = getMediaInfo(iconUrl), poster = getMediaInfo(coverUrl))
    }

suspend fun joinCommunity(
    uid: PrimaryKey,
    communityId: PrimaryKey
) = runCatching {
    DatabaseFactory.dbQuery {
        createCommunityJoin(uid, communityId)
    }.insertedCount > 0
}

suspend fun searchCommunities(
    word: String,
    backend: Backend,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int
): Result<Pair<List<CommunityInfo>, Long>> {
    return runCatching {
        val list = DatabaseFactory.mapQuery({
            Triple(toCommunityIfo(null), icon, poster)
        }, Community::wrapRow) {
            val query = Community.find {
                Communities.name like "%$word%"
            }
            query.bindPaginationQuery(Communities, prePageToken, nextPageToken, size)
        }
        val count = DatabaseFactory.count {
            Community.find {
                Communities.name like "%$word%"
            }
        }
        parseCommunityList(backend, list) to count
    }
}

suspend fun searchJoinedCommunities(
    uid: PrimaryKey,
    backend: Backend,
    pre: PrimaryKey?,
    next: PrimaryKey?,
    size: Int
): Result<Pair<List<CommunityInfo>, Long>> {
    return runCatching {
        val list = DatabaseFactory.mapQuery({
            val (community, joinTime) = this
            Triple(community.toCommunityIfo(joinTime), community.icon, community.poster)
        }, {
            val community = Community.wrapRow(it)
            val joinTime = it[CommunityJoins.joinTime]
            community to joinTime
        }) {
            val query = Communities.join(CommunityJoins, JoinType.INNER, Communities.id, CommunityJoins.communityId)
                .select(Communities.fields + CommunityJoins.joinTime)
                .where {
                    CommunityJoins.uid eq uid
                }
            query.bindPaginationQuery(Communities, pre, next, size)
        }
        val count = DatabaseFactory.dbQuery {
            Communities.join(CommunityJoins, JoinType.INNER, Communities.id, CommunityJoins.communityId)
                .selectAll()
                .where {
                    CommunityJoins.uid eq uid
                }.count()
        }
        parseCommunityList(backend, list) to count
    }
}

private fun parseCommunityList(
    backend: Backend,
    list: List<Triple<CommunityInfo, String?, String?>>
): List<CommunityInfo> {
    val icons = backend.mediaService.get("apic", list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    })
    return list.mapIndexed { i, communityPair ->
        val first = icons[i * 2]
        val second = icons[i * 2 + 1]
        communityPair.first.copy(icon = getMediaInfo(first), poster = getMediaInfo(second))
    }
}
