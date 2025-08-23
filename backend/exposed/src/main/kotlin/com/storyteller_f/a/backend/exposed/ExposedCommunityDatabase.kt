package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.CommunityDatabase
import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.MemberJoin
import com.storyteller_f.a.backend.core.types.RawCommunity
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.exposed.query.batchCreateCommunityRooms
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildCommunitySearchQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.Communities
import com.storyteller_f.a.backend.exposed.tables.MemberJoins
import com.storyteller_f.a.backend.exposed.tables.UserTopicReads
import com.storyteller_f.a.backend.exposed.tables.addJoinRaw
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.type.JoinStatusSearch.JOINED
import com.storyteller_f.shared.type.JoinStatusSearch.NOT_JOINED
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedCommunityDatabase(
    val exposedDatabaseSession: ExposedDatabaseSession,
    val containerDatabase: ContainerDatabase
) : CommunityDatabase {
    override suspend fun checkCommunityExists(parentId: PrimaryKey): Result<List<Long>> {
        return exposedDatabaseSession.dbSearch {
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
    }

    override suspend fun getRawCommunity(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean?,
        uid: PrimaryKey?
    ): Result<RawCommunity?> {
        if (uid == null && fillJoinInfo == true) {
            return Result.failure(UnauthorizedException())
        }
        return exposedDatabaseSession.dbSearch {
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
            processCommunityToRawCommunity(uid, listOf(community)).map {
                it.first()
            }
        }
    }

    override suspend fun getJoinedCommunityIds(uid: PrimaryKey): Result<List<Long>> {
        return exposedDatabaseSession.dbSearch {
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
    }

    override suspend fun getCommunityPaginationResult(
        word: String?,
        hasPosterSearch: PosterSearch?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch
    ): Result<PaginationResult<RawCommunity>?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                    .select(Communities.fields + Aids.value)
                    .buildCommunitySearchQuery(joinSearch, word, hasPosterSearch)
                    .bindPaginationQuery(Communities, primaryKeyFetch)
            }
            map(Community::wrapRow)
        }.mapResultIfNotNull { list ->
            exposedDatabaseSession.dbSearch {
                search {
                    Communities.select(Communities.id)
                        .buildCommunitySearchQuery(joinSearch, word, hasPosterSearch)
                }
                count()
            }.mapResult { count ->
                val uid = when (joinSearch) {
                    is JoinSearch.Joined -> joinSearch.uid
                    is JoinSearch.NotJoined -> joinSearch.uid
                    is JoinSearch.Unspecified -> joinSearch.uid
                }
                processCommunityToRawCommunity(uid, list).map { list ->
                    PaginationResult(list, count)
                }
            }
        }
    }

    suspend fun processCommunityToRawCommunity(
        uid: PrimaryKey?,
        communities: List<Community>
    ): Result<List<RawCommunity>> {
        return containerDatabase.getContainerInfo(communities.map { it.id }, uid)
            .map { (joinedTimeMap, lastReadMap, memberCountMap) ->
                communities.map {
                    RawCommunity(
                        it,
                        joinedTimeMap[it.id]?.joinedTime,
                        lastReadMap[it.id]?.topicId,
                        memberCountMap[it.id] ?: 0
                    )
                }
            }
    }

    override suspend fun createCommunity(community: Community): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            check(Communities.insert {
                it[Communities.id] = community.id
                it[Communities.name] = community.name
                it[Communities.owner] = community.owner
                it[Communities.createdTime] = community.createdTime
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
            MemberJoin.addJoinRaw(
                community.owner,
                community.id,
                community.createdTime,
                ObjectType.COMMUNITY
            )
        }
    }

    override suspend fun createCommunityRooms(rooms: List<Room>): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            batchCreateCommunityRooms(rooms)
        }
    }

    override suspend fun getCommunityJoinedTimeByIds(
        uid: PrimaryKey,
        communityIds: List<PrimaryKey>
    ): Result<List<Pair<Long, LocalDateTime>>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Communities.join(
                    MemberJoins,
                    JoinType.INNER,
                    Communities.id,
                    MemberJoins.objectId
                ) {
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
    }

    override suspend fun getRawCommunities(
        objectListFetch: ObjectListFetch
    ): Result<List<RawCommunity>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                    .selectAll().where {
                        when (objectListFetch) {
                            is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                            is ObjectListFetch.IdListFetch -> Communities.id inList objectListFetch.idList
                        }
                    }
            }
            map {
                val community = Community.wrapRow(it)
                val joinedTime = it.getOrNull(MemberJoins.joinedTime)
                val lastRead = it.getOrNull(UserTopicReads.topicId)
                val communityInfo = community
                RawCommunity(
                    communityInfo,
                    joinedTime,
                    lastRead,
                    0
                )
            }
        }
    }

    override suspend fun updateCommunity(
        id: PrimaryKey,
        body: UpdateCommunityBody
    ): Result<Boolean> {
        return exposedDatabaseSession.dbQuery {
            listOf(suspend {
                val newIcon = body.icon
                val newName = body.name
                val newPoster = body.poster
                if (!newName.isNullOrBlank() || newIcon != null || newPoster != null) {
                    Communities.update({
                        Communities.id eq id
                    }) {
                        if (!newName.isNullOrBlank()) {
                            it[Communities.name] = newName
                        }
                        if (newIcon != null) {
                            it[Communities.icon] = newIcon
                        }
                        if (newPoster != null) {
                            it[Communities.poster] = newPoster
                        }
                    } > 0
                } else {
                    true
                }
            }).all {
                it()
            }
        }
    }
}

fun JoinStatusSearch?.toJoinSearch(uid: PrimaryKey?): JoinSearch {
    when (this) {
        JOINED -> {
            if (uid == null) throw UnauthorizedException()
            return JoinSearch.Joined(uid)
        }

        NOT_JOINED -> {
            if (uid == null) throw UnauthorizedException()
            return JoinSearch.NotJoined(uid)
        }

        else -> return JoinSearch.Unspecified(uid)
    }
}
