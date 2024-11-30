package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.UnauthorizedException
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.Communities
import com.storyteller_f.tables.Community
import com.storyteller_f.tables.MemberJoins
import com.storyteller_f.tables.addCommunityJoin
import com.storyteller_f.tables.exit
import io.ktor.resources.*
import io.ktor.server.plugins.BadRequestException
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.JoinType

@Resource("/communities")
class RouteCommunities(val aid: String? = null, val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteCommunities = RouteCommunities(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null
    )

    @Resource("{id}")
    class Id(val parent: RouteCommunities = RouteCommunities(), val id: PrimaryKey) {
        @Resource("members")
        class Members(val parent: Id, val word: String?)

        @Resource("join")
        class Join(val parent: Id)

        @Resource("exit")
        class Exit(val parent: Id)
    }
}

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

suspend fun getCommunity(
    communityId: PrimaryKey?,
    communityAid: String?,
    backend: Backend,
    id: PrimaryKey?,
    fillJoinInfo: Boolean?
): Result<CommunityInfo?> {
    return DatabaseFactory.queryNotNull({
        Triple(first.toCommunityIfo(second), first.icon, first.poster)
    }, {
        Community.wrapRow(it) to if (fillJoinInfo == true) it[MemberJoins.joinTime] else null
    }) {
        when {
            fillJoinInfo != true -> Community.find {
                if (communityId != null) {
                    Communities.id eq communityId
                } else if (communityAid != null) {
                    Communities.aid eq communityAid
                } else {
                    throw BadRequestException("aid must be set.")
                }
            }

            id == null -> throw UnauthorizedException()
            else -> Communities.join(MemberJoins, JoinType.LEFT, Communities.id, MemberJoins.objectId) {
                MemberJoins.uid eq id
            }.select(Communities.fields + MemberJoins.joinTime)
                .where {
                    if (communityId != null) {
                        Communities.id eq communityId
                    } else if (communityAid != null) {
                        Communities.aid eq communityAid
                    } else {
                        throw BadRequestException("aid must be set.")
                    }
                }
        }.limit(1).firstOrNull()
    }.mapResultNotNull { (info, iconName, coverName) ->
        backend.mediaService.get("apic", listOf(iconName, coverName)).map { (iconUrl, coverUrl) ->
            info.copy(icon = getMediaInfo(iconUrl), poster = getMediaInfo(coverUrl))
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
