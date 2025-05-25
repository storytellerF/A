package com.storyteller_f.tables

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.*
import com.storyteller_f.shared.type.JoinSearch
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.type.toJoinSearch
import com.storyteller_f.shared.utils.*
import com.storyteller_f.types.PagingFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = communityIcon()
    val poster = communityPoster().index()
    val owner = customPrimaryKey("owner").index()
}

class Community(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val icon: String? = null,
    val poster: String? = null
) :
    BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Community {
            return with(Communities) {
                Community(
                    row[id],
                    row[createdTime],
                    row[Aids.value],
                    row[name],
                    row[owner],
                    row[icon],
                    row[poster]
                )
            }
        }
    }
}

fun Community.toCommunityIfo(
    memberCount: Long,
    joinTime: LocalDateTime?,
    lastRead: PrimaryKey?
) = CommunityInfo(
    id,
    aid,
    name,
    owner,
    createdTime,
    memberCount,
    joinedTime = joinTime,
    lastRead = lastRead
)

suspend fun DatabaseFactory.checkCommunityExists(backend: Backend, parentId: PrimaryKey) =
    dbSearch(backend) {
        search {
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
                .where {
                    Communities.id eq parentId
                }
        }
        map {
            it[Communities.id]
        }
    }

fun mapCommunityInfo(it: ResultRow): CommunityRawResult {
    val community = Community.wrapRow(it)
    val joinedTime = it.getOrNull(MemberJoins.joinedTime)
    val lastRead = it.getOrNull(UserTopicReads.topicId)
    val communityInfo = community.toCommunityIfo(0, joinedTime, lastRead)
    return CommunityRawResult(communityInfo, community.icon, community.poster)
}

data class CommunityRawResult(val communityInfo: CommunityInfo, val icon: String?, val poster: String?)

suspend fun DatabaseFactory.getCommunity(
    backend: Backend,
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    uid: PrimaryKey? = null
): Result<CommunityRawResult?> {
    if (uid == null && fillJoinInfo == true) {
        return Result.failure(UnauthorizedException())
    }
    return dbSearch(backend) {
        search {
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
                .where(buildCommunityWhereClause(objectFetch))
        }
        first(Community::wrapRow)
    }.mapResultIfNotNull { community ->
        processCommunitiesInfo(uid, listOf(community), backend).map {
            it.first()
        }
    }
}

private fun buildCommunityWhereClause(
    objectFetch: ObjectFetch
): SqlExpressionBuilder.() -> Op<Boolean> = {
    when (objectFetch) {
        is ObjectFetch.AidFetch -> Aids.value eq objectFetch.aid
        is ObjectFetch.IdFetch -> Communities.id eq objectFetch.id
    }
}

suspend fun DatabaseFactory.getJoinedCommunityIds(backend: Backend, uid: PrimaryKey) =
    dbSearch(backend) {
        search {
            Communities
                .join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
                    MemberJoins.uid eq uid
                }.select(Communities.id)
        }
        map {
            it[Communities.id]
        }
    }

fun Query.bindPosterSearch(
    hasPosterSearch: PosterSearch?
): Query {
    when (hasPosterSearch) {
        PosterSearch.HAS_POSTER -> andWhere {
            Communities.poster.isNotNull()
        }

        PosterSearch.NO_POSTER -> andWhere {
            Communities.poster.isNull()
        }

        else -> {}
    }
    return this
}

suspend fun DatabaseFactory.getPaginationCommunityList(
    backend: Backend,
    uid: PrimaryKey?,
    joinStatus: JoinStatusSearch?,
    word: String?,
    hasPosterSearch: PosterSearch?,
    pagingFetch: PagingFetch
): Result<Pair<List<CommunityRawResult>, Long>?> {
    val joinSearch = joinStatus.toJoinSearch(uid)

    return dbSearch(backend) {
        search {
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
                .buildCommunitySearchQuery(joinSearch, word, hasPosterSearch)
                .bindPaginationQuery(Communities, pagingFetch)
        }
        map(Community::wrapRow)
    }.mapResultIfNotNull {
        processCommunitiesInfo(uid, it, backend).mapResult { list ->
            count(backend) {
                Communities.select(Communities.id)
                    .buildCommunitySearchQuery(joinStatus.toJoinSearch(uid), word, hasPosterSearch)
            }.map { count ->
                list to count
            }
        }
    }
}

private suspend fun DatabaseFactory.processCommunitiesInfo(
    uid: PrimaryKey?,
    communities: List<Community>,
    backend: Backend
): Result<List<CommunityRawResult>> = if (uid == null) {
    Result.success(Triple(emptyMap(), emptyMap(), emptyMap()))
} else {
    getContainerInfo(backend, communities.map { it.id }, uid)
}.map { (joinedTimeMap, lastReadMap, memberCountMap) ->
    communities.map {
        val communityInfo =
            it.toCommunityIfo(
                memberCountMap[it.id] ?: 0,
                joinedTimeMap[it.id]?.joinedTime,
                lastReadMap[it.id]?.topicId
            )
        CommunityRawResult(communityInfo, it.icon, it.poster)
    }
}

suspend fun DatabaseFactory.getContainerInfo(
    backend: Backend,
    parentIds: List<PrimaryKey>,
    uid: PrimaryKey
): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>> = merge({
    getUserJoinedTime(backend, parentIds, uid).map {
        it.associateBy { memberJoin ->
            memberJoin.objectId
        }
    }
}, {
    getReadLogs(backend, parentIds, uid).map {
        it.associateBy { userTopicRead ->
            userTopicRead.objectId
        }
    }
}, {
    getMemberCount(backend, parentIds).map {
        it.associateByPair()
    }
})

private fun Query.buildCommunitySearchQuery(
    joinSearch: JoinSearch,
    word: String?,
    hasPosterSearch: PosterSearch?
): Query {
    when (joinSearch) {
        is JoinSearch.Joined -> {
            adjustColumnSet {
                this.join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
                    MemberJoins.uid eq joinSearch.uid
                }
            }
        }

        is JoinSearch.NotJoined -> {
            where {
                Communities.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
                    MemberJoins.uid eq joinSearch.uid
                })
            }
        }

        else -> {
        }
    }
    if (!word.isNullOrBlank()) {
        andWhere {
            Communities.name like "$word%"
        }
    }
    bindPosterSearch(hasPosterSearch)
    return this
}

suspend fun DatabaseFactory.createCommunity(backend: Backend, community: Community) = dbQuery(
    backend
) {
    check(Communities.insert {
        it[id] = community.id
        it[name] = community.name
        it[owner] = community.owner
        it[createdTime] = community.createdTime
    }.insertedCount > 0) {
        "insert community failed"
    }
    check(Aids.insert {
        it[value] = community.aid
        it[objectId] = community.id
        it[objectType] = ObjectType.COMMUNITY
    }.insertedCount > 0) {
        "insert aid failed"
    }
    addCommunityJoinRaw(community.owner, community.id, community.createdTime)
    createCommunityRoomsRaw(community.id, community.owner, community.aid)
}

suspend fun createCommunityRoomsRaw(
    id: PrimaryKey,
    ownerUid: PrimaryKey,
    communityAid: String
) {
    batchCreateCommunityRooms(
        listOf(
            "${communityAid}_general" to "General",
            "${communityAid}_lobby" to "Lobby",
            "${communityAid}_support" to "Support"
        ).map { pair ->
            Room(SnowflakeFactory.nextId(), now(), pair.first, pair.second, ownerUid, communityId = id)
        }
    )
}

suspend fun DatabaseFactory.getCommunityJoinedTimeByIds(
    backend: Backend,
    uid: PrimaryKey,
    communityIds: List<PrimaryKey>
) = dbSearch(backend) {
    search {
        Communities.join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
            MemberJoins.uid eq uid
        }.select(Communities.id, MemberJoins.joinedTime)
            .where {
                Communities.id.inList(communityIds)
            }
    }
    map {
        it[Communities.id] to it[MemberJoins.joinedTime]
    }
}

suspend fun DatabaseFactory.getCommunityByIds(
    backend: Backend,
    idList: List<PrimaryKey>
) = dbSearch(backend) {
    search {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .selectAll().where {
                Communities.id inList idList
            }
    }
    map(::mapCommunityInfo)
}

suspend fun DatabaseFactory.getCommunityByAids(
    backend: Backend,
    idList: List<String>
) = dbSearch(backend) {
    search {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId).selectAll().where {
            Aids.value inList idList
        }
    }
    map(::mapCommunityInfo)
}

suspend fun processCommunityList(
    backend: Backend,
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>?> {
    return DatabaseFactory.getMediaInfoList(backend, list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }).mapIfNotNull { icons ->
        list.mapIndexed { i, communityPair ->
            val first = icons[i * 2]
            val second = icons[i * 2 + 1]
            communityPair.communityInfo.copy(icon = first, poster = second, hasPoster = second != null)
        }
    }
}

suspend fun DatabaseFactory.updateCommunity(
    backend: Backend,
    id: PrimaryKey,
    body: UpdateCommunityBody
) = dbQuery(backend) {
    listOf {
        val newIcon = body.icon
        val newName = body.name
        val newPoster = body.poster
        if (!newName.isNullOrBlank() || newIcon != null || newPoster != null) {
            Communities.update({
                Communities.id eq id
            }) {
                if (!newName.isNullOrBlank()) {
                    it[name] = newName
                }
                if (newIcon != null) {
                    it[icon] = newIcon.ifEmpty { null }
                }
                if (newPoster != null) {
                    it[poster] = newPoster.ifEmpty { null }
                }
            } > 0
        } else {
            true
        }
    }.all {
        it()
    }
}
