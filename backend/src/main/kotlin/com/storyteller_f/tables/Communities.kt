package com.storyteller_f.tables

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.count
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.*
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
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

suspend fun Backend.checkCommunityExists(parentId: PrimaryKey) =
    databaseSession.dbSearch {
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

suspend fun Backend.getCommunityRawResult(
    objectFetch: ObjectFetch,
    fillJoinInfo: Boolean? = null,
    uid: PrimaryKey? = null
): Result<CommunityRawResult?> {
    if (uid == null && fillJoinInfo == true) {
        return Result.failure(UnauthorizedException())
    }
    return databaseSession.dbSearch {
        search {
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
                .where {
                    when (objectFetch) {
                        is ObjectFetch.AidFetch -> Aids.value eq objectFetch.aid
                        is ObjectFetch.IdFetch -> Communities.id eq objectFetch.id
                    }
                }
        }
        first(Community::wrapRow)
    }.mapResultIfNotNull { community ->
        processCommunityToCommunityRawResult(uid, listOf(community)).map {
            it.first()
        }
    }
}

suspend fun Backend.getJoinedCommunityIds(uid: PrimaryKey) =
    databaseSession.dbSearch {
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

suspend fun Backend.getCommunityPaginationResult(
    uid: PrimaryKey?,
    joinStatus: JoinStatusSearch?,
    word: String?,
    hasPosterSearch: PosterSearch?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<CommunityRawResult>?> {
    val joinSearch = joinStatus.toJoinSearch(uid)

    return databaseSession.dbSearch {
        search {
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
                .buildCommunitySearchQuery(joinSearch, word, hasPosterSearch)
                .bindPaginationQuery(Communities, primaryKeyFetch)
        }
        map(Community::wrapRow)
    }.mapResultIfNotNull {
        processCommunityToCommunityRawResult(uid, it).mapResult { list ->
            databaseSession.dbSearch {
                search {
                    Communities.select(Communities.id)
                        .buildCommunitySearchQuery(joinStatus.toJoinSearch(uid), word, hasPosterSearch)
                }
                count()
            }.map { count ->
                PaginationResult(list, count)
            }
        }
    }
}

private suspend fun Backend.processCommunityToCommunityRawResult(
    uid: PrimaryKey?,
    communities: List<Community>
): Result<List<CommunityRawResult>> = getContainerInfo(
    communities.map { it.id },
    uid
).map { (joinedTimeMap, lastReadMap, memberCountMap) ->
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

suspend fun Backend.getContainerInfo(
    parentIds: List<PrimaryKey>,
    uid: PrimaryKey?
): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>> = merge({
    if (uid != null) {
        getUserJoinedTime(parentIds, uid).map {
            it.associateBy { memberJoin ->
                memberJoin.objectId
            }
        }
    } else {
        Result.success(emptyMap())
    }
}, {
    if (uid != null) {
        getTopicReadList(parentIds, uid).map {
            it.associateBy { userTopicRead ->
                userTopicRead.objectId
            }
        }
    } else {
        Result.success(emptyMap())
    }
}, {
    getMemberCount(parentIds).map {
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

suspend fun Backend.createCommunity(community: Community) = databaseSession.dbQuery {
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

suspend fun Backend.getCommunityJoinedTimeByIds(
    uid: PrimaryKey,
    communityIds: List<PrimaryKey>
) = databaseSession.dbSearch {
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

suspend fun Backend.getCommunityRawResults(
    objectListFetch: ObjectListFetch
) = databaseSession.dbSearch {
    search {
        Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
            .selectAll().where {
                when (objectListFetch) {
                    is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                    is ObjectListFetch.IdListFetch -> Communities.id inList objectListFetch.idList
                }
            }
    }
    map(::mapCommunityInfo)
}

suspend fun Backend.processCommunityRawResultToCommunityInfo(
    list: List<CommunityRawResult>
): Result<List<CommunityInfo>?> {
    return getMediaInfoList(list.flatMap { (_, icon, poster) ->
        listOf(icon, poster)
    }).mapIfNotNull { icons ->
        list.mapIndexed { i, communityPair ->
            val first = icons[i * 2]
            val second = icons[i * 2 + 1]
            communityPair.communityInfo.copy(icon = first, poster = second, hasPoster = second != null)
        }
    }
}

suspend fun Backend.updateCommunity(
    id: PrimaryKey,
    body: UpdateCommunityBody
) = databaseSession.dbQuery {
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
