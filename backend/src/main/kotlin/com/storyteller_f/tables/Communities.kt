package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val aid = varchar("aid", COMMUNITY_ID_LENGTH).uniqueIndex()
    val name = varchar("name", COMMUNITY_NAME_LENGTH).index()
    val icon = varchar("icon", ICON_LENGTH).nullable()
    val owner = customPrimaryKey("owner").index()
    val poster = varchar("poster", ICON_LENGTH).nullable()
}

class Community(
    val aid: String,
    val name: String,
    val icon: String?,
    val owner: PrimaryKey,
    val poster: String?,
    id: PrimaryKey,
    createdTime: LocalDateTime
) :
    BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Community {
            return Community(
                row[Communities.aid],
                row[Communities.name],
                row[Communities.icon],
                row[Communities.owner],
                row[Communities.poster],
                row[Communities.id],
                row[Communities.createdTime]
            )
        }

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>) = Communities.selectAll().where(function)

        fun findById(id: PrimaryKey) = Communities.selectAll().where {
            Communities.id eq id
        }

        fun new(community: Community): PrimaryKey {
            val id = Communities.insert {
                it[id] = community.id
                it[name] = community.name
                it[owner] = community.owner
                it[aid] = community.aid
                it[createdTime] = community.createdTime
            }[Communities.id]
            assert(id == community.id)
            return id
        }
    }
}

fun findCommunityByAId(aid: String): ResultRow? {
    return Communities.selectAll().where {
        Communities.aid eq aid
    }.limit(1).firstOrNull()
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

suspend fun getCommunitySource(parentId: PrimaryKey): Result<Community?> = DatabaseFactory.first({
    this
}, Community::wrapRow) {
    Community.findById(parentId)
}

suspend fun getCommonCommunity(
    fillJoinInfo: Boolean?,
    communityId: PrimaryKey?,
    communityAid: String?,
    id: PrimaryKey?
): Result<Triple<CommunityInfo, String?, String?>?> = DatabaseFactory.first({
    Triple(first.toCommunityIfo(second), first.icon, first.poster)
}, {
    Community.wrapRow(it) to if (fillJoinInfo == true) it[MemberJoins.joinTime] else null
}) {
    when {
        fillJoinInfo != true -> Community.find(buildCommunityWhereClause(communityId, communityAid))
        id == null -> throw UnauthorizedException()
        else -> Communities.join(MemberJoins, JoinType.LEFT, Communities.id, MemberJoins.objectId) {
            MemberJoins.uid eq id
        }.select(Communities.fields + MemberJoins.joinTime)
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
        Communities.aid eq communityAid
    } else {
        throw CustomBadRequestException("aid must be set.")
    }
}

suspend fun commonCommunityList(
    uid: PrimaryKey?,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    joinStatus: JoinStatusSearch?,
    word: String?
): Result<List<Triple<CommunityInfo, String?, String?>>> = DatabaseFactory.mapQuery({
    Triple(toCommunityIfo(null), icon, poster)
}, Community::wrapRow) {
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

suspend fun commonPaginationCommunityList(
    uid: PrimaryKey?,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    joinStatus: JoinStatusSearch?,
    word: String?
): Result<Pair<List<Triple<CommunityInfo, String?, String?>>, Long>> {
    return commonCommunityList(uid, prePageToken, nextPageToken, size, joinStatus, word).mapResult { list ->
        DatabaseFactory.count {
            getSearchCommunityQuery(uid, true, joinStatus, word)
        }.map { value ->
            list to value
        }
    }
}