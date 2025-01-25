package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = communityIcon()
    val poster = communityPoster()
    val owner = customPrimaryKey("owner").index()
}

class Community(
    val aid: String,
    val name: String,
    val icon: String? = null,
    val owner: PrimaryKey,
    val poster: String? = null,
    id: PrimaryKey,
    createdTime: LocalDateTime
) :
    BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Community {
            return Community(
                row[Aids.value],
                row[Communities.name],
                row[Communities.icon],
                row[Communities.owner],
                row[Communities.poster],
                row[Communities.id],
                row[Communities.createdTime]
            )
        }

        fun findById(id: PrimaryKey) = Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .select(Communities.fields + Aids.value)
            .where {
                Communities.id eq id
            }

        fun new(community: Community): Boolean {
            return Communities.insert {
                it[id] = community.id
                it[name] = community.name
                it[owner] = community.owner
                it[createdTime] = community.createdTime
            }.insertedCount > 0
        }
    }
}

fun findCommunityByAId(aid: String): Query {
    return Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId).selectAll().where {
        Aids.value eq aid
    }.limit(1)
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

suspend fun DatabaseFactory.checkCommunityExists(parentId: PrimaryKey) = first({
    it[Communities.id]
}) {
    Community.findById(parentId)
}

data class CommunityRawResult(val communityInfo: CommunityInfo, val icon: String?, val poster: String?)

suspend fun DatabaseFactory.getCommonCommunity(
    fillJoinInfo: Boolean?,
    communityId: PrimaryKey?,
    communityAid: String?,
    id: PrimaryKey?
): Result<CommunityRawResult?> = first({
    CommunityRawResult(first.toCommunityIfo(second), first.icon, first.poster)
}, {
    Community.wrapRow(it) to if (fillJoinInfo == true) it[MemberJoins.joinTime] else null
}) {
    when {
        fillJoinInfo != true -> Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .select(Communities.fields + Aids.value)
            .where(buildCommunityWhereClause(communityId, communityAid))

        id == null -> throw UnauthorizedException()
        else -> Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .join(MemberJoins, JoinType.LEFT, Communities.id, MemberJoins.objectId) {
                MemberJoins.uid eq id
            }.select(Communities.fields + MemberJoins.joinTime + Aids.value)
            .where(buildCommunityWhereClause(communityId, communityAid))
    }
}

private fun buildCommunityWhereClause(
    communityId: PrimaryKey?,
    communityAid: String?
): SqlExpressionBuilder.() -> Op<Boolean> = {
    if (communityId != null) {
        Communities.id eq communityId
    } else if (communityAid != null) {
        Aids.value eq communityAid
    } else {
        throw CustomBadRequestException("aid must be set.")
    }
}

suspend fun DatabaseFactory.commonCommunityList(
    uid: PrimaryKey?,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    joinStatus: JoinStatusSearch?,
    word: String?
): Result<List<CommunityRawResult>> = mapQuery({
    CommunityRawResult(first.toCommunityIfo(null), first.icon, first.poster)
}, {
    Community.wrapRow(it) to it.getOrNull(MemberJoins.joinTime)
}) {
    getSearchCommunityQuery(uid, false, joinStatus, word).bindPaginationQuery(
        Communities,
        prePageToken,
        nextPageToken,
        size
    )
}

fun getSearchCommunityQuery(
    uid: PrimaryKey?,
    getCount: Boolean,
    joinStatusSearch: JoinStatusSearch?,
    word: String?
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
            Communities.name like "%$word%"
        }
    }
    return query
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

suspend fun DatabaseFactory.commonPaginationCommunityList(
    uid: PrimaryKey?,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    joinStatus: JoinStatusSearch?,
    word: String?
): Result<Pair<List<CommunityRawResult>, Long>> {
    return commonCommunityList(uid, prePageToken, nextPageToken, size, joinStatus, word).mapResult { list ->
        count {
            getSearchCommunityQuery(uid, true, joinStatus, word)
        }.map { value ->
            list to value
        }
    }
}

suspend fun DatabaseFactory.doCreateCommunity(community: Community) = dbQuery {
    Community.new(community) && Aids.insert {
        it[value] = community.aid
        it[objectId] = community.id
        it[objectType] = ObjectType.COMMUNITY
    }.insertedCount > 0
}

suspend fun DatabaseFactory.getCommunityByIds(
    uid: PrimaryKey,
    communityIds: List<PrimaryKey>
) = mapQuery({
    this[Communities.id] to this[MemberJoins.joinTime]
}) {
    Communities.join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
        MemberJoins.uid eq uid
    }.select(Communities.id, MemberJoins.joinTime)
        .where {
            Communities.id.inList(communityIds)
        }
}
