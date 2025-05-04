package com.storyteller_f.tables

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.types.PagingFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = communityIcon()
    val poster = communityPoster().index()
    val owner = customPrimaryKey("owner").index()
    val memberCount = long("member_count")
}

class Community(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val memberCount: Long,
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
                    row[memberCount],
                    row[icon],
                    row[poster]
                )
            }
        }
    }
}

fun Community.toCommunityIfo(
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
    first(backend, {
        it[Communities.id]
    }) {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .select(Communities.fields + Aids.value)
            .where {
                Communities.id eq parentId
            }
    }

fun mapCommunityInfo(it: ResultRow): CommunityRawResult {
    val community = Community.wrapRow(it)
    val joinedTime = it.getOrNull(MemberJoins.joinedTime)
    val lastRead = it.getOrNull(UserTopicReads.topicId)
    return CommunityRawResult(community.toCommunityIfo(joinedTime, lastRead), community.icon, community.poster)
}

data class CommunityRawResult(val communityInfo: CommunityInfo, val icon: String?, val poster: String?)

suspend fun DatabaseFactory.getCommunity(
    backend: Backend,
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    id: PrimaryKey? = null
): Result<CommunityRawResult?> = first(backend, ::mapCommunityInfo) {
    when {
        fillJoinInfo != true -> {
            val select = Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
            if (id != null) {
                select.adjustColumnSet {
                    join(UserTopicReads, JoinType.LEFT, Communities.id, UserTopicReads.objectId) {
                        UserTopicReads.uid eq id
                    }
                }.adjustSelect {
                    select(Communities.fields + Aids.value + UserTopicReads.topicId)
                }
            }
            select.where(buildCommunityWhereClause(objectFetch))
        }

        id != null -> Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .join(MemberJoins, JoinType.LEFT, Communities.id, MemberJoins.objectId) {
                MemberJoins.uid eq id
            }
            .select(Communities.fields + MemberJoins.joinedTime + Aids.value)
            .where(buildCommunityWhereClause(objectFetch))

        else -> throw UnauthorizedException()
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
    mapQuery(backend, {
        it[Communities.id]
    }) {
        Communities
            .join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
                MemberJoins.uid eq uid
            }.select(Communities.id)
    }

fun getSearchCommunityQuery(
    uid: PrimaryKey?,
    getCount: Boolean,
    joinStatusSearch: JoinStatusSearch?,
    word: String?,
    hasPosterSearch: PosterSearch?
): Query {
    val query = when (joinStatusSearch) {
        JoinStatusSearch.JOINED -> {
            if (uid != null) {
                getUserJoinedCommunityQuery(uid, getCount)
            } else {
                throw UnauthorizedException()
            }
        }

        JoinStatusSearch.NOT_JOINED -> {
            if (uid != null) {
                Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                    .join(UserTopicReads, JoinType.LEFT, Communities.id, UserTopicReads.objectId) {
                        UserTopicReads.uid eq uid
                    }
                    .select(Communities.fields + Aids.value + UserTopicReads.topicId)
                    .where {
                        Communities.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
                            MemberJoins.uid eq uid
                        })
                    }
            } else {
                throw UnauthorizedException()
            }
        }

        else -> {
            val join = Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
            if (uid != null) {
                join.adjustColumnSet {
                    join(UserTopicReads, JoinType.LEFT, Rooms.id, UserTopicReads.objectId) {
                        UserTopicReads.uid eq uid
                    }
                }.adjustSelect {
                    select(Communities.fields + Aids.value + UserTopicReads.topicId)
                }
            }
            join
        }
    }
    if (!(word.isNullOrBlank())) {
        query.andWhere {
            Communities.name like "$word%"
        }
    }
    query.bindPosterSearch(hasPosterSearch)
    return query
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

fun getUserJoinedCommunityQuery(
    uid: PrimaryKey,
    getCount: Boolean
): Query {
    val join = Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
        .join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
            MemberJoins.uid eq uid
        }
    return if (getCount) {
        join.selectAll()
    } else {
        join.join(UserTopicReads, JoinType.LEFT, Communities.id, UserTopicReads.objectId) {
            UserTopicReads.uid eq uid
        }.select(Communities.fields + MemberJoins.joinedTime + Aids.value + UserTopicReads.topicId)
    }
}

suspend fun DatabaseFactory.getPaginationCommunityList(
    backend: Backend,
    uid: PrimaryKey?,
    joinStatus: JoinStatusSearch?,
    word: String?,
    hasPosterSearch: PosterSearch?,
    pagingFetch: PagingFetch
) = mapQuery(backend, ::mapCommunityInfo) {
    getSearchCommunityQuery(uid, false, joinStatus, word, hasPosterSearch).bindPaginationQuery(
        Communities,
        pagingFetch
    )
}.mapResult { list ->
    count(backend) {
        getSearchCommunityQuery(uid, true, joinStatus, word, hasPosterSearch)
    }.map { value ->
        list to value
    }
}

suspend fun DatabaseFactory.createCommunity(backend: Backend, community: Community) = dbQuery(
    backend
) {
    check(Communities.insert {
        it[id] = community.id
        it[name] = community.name
        it[owner] = community.owner
        it[createdTime] = community.createdTime
        it[memberCount] = community.memberCount
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
    addCommunityJoinRaw(community.owner, community.id, community.createdTime, community.memberCount)
    createCommunityRooms(community.id, community.owner, community.aid)
}

suspend fun createCommunityRooms(
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
            Room(SnowflakeFactory.nextId(), now(), pair.first, pair.second, ownerUid, 1, communityId = id)
        }
    )
}

suspend fun DatabaseFactory.getCommunityJoinedTimeByIds(
    backend: Backend,
    uid: PrimaryKey,
    communityIds: List<PrimaryKey>
) = mapQuery(backend, {
    it[Communities.id] to it[MemberJoins.joinedTime]
}) {
    Communities.join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
        MemberJoins.uid eq uid
    }.select(Communities.id, MemberJoins.joinedTime)
        .where {
            Communities.id.inList(communityIds)
        }
}

suspend fun DatabaseFactory.getCommunityByIds(
    backend: Backend,
    idList: List<PrimaryKey>
): Result<List<CommunityRawResult>> {
    return mapQuery(backend, {
        CommunityRawResult(toCommunityIfo(null, null), icon, poster)
    }, Community::wrapRow) {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId).selectAll().where {
            Communities.id inList idList
        }
    }
}

suspend fun DatabaseFactory.getCommunityByAids(
    backend: Backend,
    idList: List<String>
): Result<List<CommunityRawResult>> {
    return mapQuery(backend, {
        CommunityRawResult(toCommunityIfo(null, null), icon, poster)
    }, Community::wrapRow) {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId).selectAll().where {
            Aids.value inList idList
        }
    }
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
            communityPair.communityInfo.copy(icon = first, poster = second)
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
        if (!newName.isNullOrBlank() || !newIcon.isNullOrBlank() || !newPoster.isNullOrBlank()) {
            Communities.update({
                Communities.id eq id
            }) {
                if (!newName.isNullOrBlank()) {
                    it[name] = newName
                }
                if (!newIcon.isNullOrBlank()) {
                    it[icon] = newIcon
                }
                if (!newPoster.isNullOrBlank()) {
                    it[poster] = newPoster
                }
            } > 0
        } else {
            true
        }
    }.all {
        it()
    }
}
