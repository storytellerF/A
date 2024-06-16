package com.storyteller_f.a.server.service

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.backend
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.JoinType


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

suspend fun getCommunity(communityId: OKey): Result<CommunityInfo?> {
    return runCatching {
        DatabaseFactory.first({ item ->
            Triple(item.toCommunityIfo(now()), item.icon, item.poster)
        }, {
            Community.wrapRow(it)
        }) {
            Community.findById(communityId)
        }?.let { (info, iconName, coverName) ->
            val (iconUrl, coverUrl) = backend.mediaService.get("apic", listOf(iconName, coverName))
            info.copy(icon = getMediaInfo(iconUrl), poster = getMediaInfo(coverUrl))
        }
    }

}

suspend fun RoutingContext.joinCommunity(
    id: OKey,
    it: OKey
) = runCatching {
    DatabaseFactory.dbQuery {
        createCommunityJoin(id, it)
    }
    Unit
}


suspend fun searchCommunities(word: String): Result<ServerResponse<CommunityInfo>> {
    return runCatching {
        communitiesResponse(DatabaseFactory.query({
            it.map { community ->
                Triple(community.toCommunityIfo(null), community.icon, community.poster)
            }
        }) {
            Community.find {
                Communities.name like "%$word%"
            }.map {
                Community.wrapRow(it)
            }
        })
    }
}

suspend fun searchJoinedCommunities(uid: OKey): Result<ServerResponse<CommunityInfo>> {
    return runCatching {
        communitiesResponse(DatabaseFactory.query({
            it.map { (community, joinTime) ->
                Triple(community.toCommunityIfo(joinTime), community.icon, community.poster)
            }
        }) {
            Communities.join(CommunityJoins, JoinType.INNER, Communities.id, CommunityJoins.communityId)
                .select(Communities.fields + CommunityJoins.joinTime)
                .where {
                    CommunityJoins.uid eq uid
                }.map { row ->
                    val community = Community.wrapRow(row)
                    val joinTime = row[CommunityJoins.joinTime]
                    community to joinTime
                }
        })
    }
}


private fun communitiesResponse(list: List<Triple<CommunityInfo, String?, String?>>): ServerResponse<CommunityInfo> {
    val icons = backend.mediaService.get("apic", list.flatMap {
        listOf(it.second, it.third)
    })
    val data = list.mapIndexed { i, communityPair ->
        val first = icons[i * 2]
        val second = icons[i * 2 + 1]
        communityPair.first.copy(icon = getMediaInfo(first), poster = getMediaInfo(second))
    }
    return ServerResponse(data, 10)
}

