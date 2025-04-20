package com.storyteller_f.tables

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
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
    BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Community {
            return Community(
                row[Communities.id],
                row[Communities.createdTime],
                row[Aids.value],
                row[Communities.name],
                row[Communities.owner],
                row[Communities.memberCount],
                row[Communities.icon],
                row[Communities.poster]
            )
        }
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
    memberCount,
    joinTime = joinTime
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

data class CommunityRawResult(val communityInfo: CommunityInfo, val icon: String?, val poster: String?)

suspend fun DatabaseFactory.getCommunity(
    backend: Backend,
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    id: PrimaryKey? = null
): Result<CommunityRawResult?> = first(backend, {
    CommunityRawResult(first.toCommunityIfo(second), first.icon, first.poster)
}, {
    Community.wrapRow(it) to if (fillJoinInfo == true) it[MemberJoins.joinTime] else null
}) {
    when {
        fillJoinInfo != true -> Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .select(Communities.fields + Aids.value)
            .where(buildCommunityWhereClause(objectFetch))

        id == null -> throw UnauthorizedException()
        else -> Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .join(MemberJoins, JoinType.LEFT, Communities.id, MemberJoins.objectId) {
                MemberJoins.uid eq id
            }.select(Communities.fields + MemberJoins.joinTime + Aids.value)
            .where(buildCommunityWhereClause(objectFetch))
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
        this[Communities.id]
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
                    .select(Communities.fields + Aids.value)
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
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
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
    target: PrimaryKey,
    getCount: Boolean
): Query {
    val join = Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
        .join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
            MemberJoins.uid eq target
        }
    return if (getCount) {
        join.selectAll()
    } else {
        join.select(Communities.fields + MemberJoins.joinTime + Aids.value)
    }
}

suspend fun DatabaseFactory.getPaginationCommunityList(
    backend: Backend,
    uid: PrimaryKey?,
    joinStatus: JoinStatusSearch?,
    word: String?,
    hasPosterSearch: PosterSearch?,
    pagingFetch: PagingFetch
): Result<Pair<List<CommunityRawResult>, Long>> {
    return mapQuery(backend, {
        CommunityRawResult(first.toCommunityIfo(null), first.icon, first.poster)
    }, {
        Community.wrapRow(it) to it.getOrNull(MemberJoins.joinTime)
    }) {
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
    this[Communities.id] to this[MemberJoins.joinTime]
}) {
    Communities.join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
        MemberJoins.uid eq uid
    }.select(Communities.id, MemberJoins.joinTime)
        .where {
            Communities.id.inList(communityIds)
        }
}

suspend fun DatabaseFactory.getCommunityByIds(
    backend: Backend,
    idList: List<PrimaryKey>
): Result<List<CommunityRawResult>> {
    return mapQuery(backend, {
        CommunityRawResult(toCommunityIfo(null), icon, poster)
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
        CommunityRawResult(toCommunityIfo(null), icon, poster)
    }, Community::wrapRow) {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId).selectAll().where {
            Aids.value inList idList
        }
    }
}

suspend fun processCommunityList(
    backend: Backend,
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>> {
    return backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }).map { icons ->
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
