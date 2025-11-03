package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.CommunityDatabase
import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.RawCommunity
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildCommunitySearchQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.Communities
import com.storyteller_f.a.backend.exposed.tables.Members
import com.storyteller_f.a.backend.exposed.tables.UserTopicReads
import com.storyteller_f.a.backend.exposed.tables.addJoin
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedCommunityDatabase(
    val databaseSession: ExposedDatabaseSession,
    val containerDatabase: ContainerDatabase
) : CommunityDatabase {

    override suspend fun getRawCommunity(
        objectFetch: ObjectFetch,
        fillJoinInfo: Boolean?,
        uid: PrimaryKey?
    ): Result<RawCommunity?> {
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
            processCommunityToRawCommunity(uid, listOf(community))
        }.mapIfNotNull {
            it.first()
        }
    }

    override suspend fun getJoinedCommunityIds(uid: PrimaryKey) = databaseSession.dbSearch {
        search {
            Communities
                .join(Members, JoinType.INNER, Communities.id, Members.objectId) {
                    Members.uid eq uid
                }.select(Communities.id)
        }
        map {
            it[Communities.id]
        }
    }

    override suspend fun getCommunityPaginationResult(
        word: String?,
        hasPosterSearch: PosterSearch?,
        primaryKeyFetch: PrimaryKeyFetch,
        joinSearch: JoinSearch
    ) = databaseSession.dbSearch {
        search {
            Communities.join(Aids, JoinType.INNER, Communities.id, Aids.objectId)
                .select(Communities.fields + Aids.value)
                .buildCommunitySearchQuery(joinSearch, word, hasPosterSearch)
                .bindPaginationQuery(Communities, primaryKeyFetch)
        }
        map(Community::wrapRow)
    }.mapResultIfNotNull { list ->
        databaseSession.dbSearch {
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

    suspend fun processCommunityToRawCommunity(
        uid: PrimaryKey?,
        communities: List<Community>
    ) = containerDatabase.getContainerInfo(communities.map { it.id }, uid).map { map ->
        communities.map {
            val containerInfo = map[it.id]
            RawCommunity(
                it,
                containerInfo?.member?.joinedTime,
                containerInfo?.userTopicRead?.topicId,
                containerInfo?.memberCount,
                containerInfo?.latestTopicId
            )
        }
    }

    override suspend fun createCommunity(community: Community, memberId: PrimaryKey) =
        databaseSession.dbQuery {
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
            addJoin(
                Member(
                    memberId,
                    community.owner,
                    community.id,
                    ObjectType.COMMUNITY,
                    community.createdTime,
                    MemberStatus.JOINED,
                    community.createdTime
                )
            )
        }

    override suspend fun getCommunityJoinedTimeByIds(
        uid: PrimaryKey,
        communityIds: List<PrimaryKey>
    ): Result<List<Pair<Long, LocalDateTime?>>> {
        if (communityIds.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
            search {
                Communities.join(
                    Members,
                    JoinType.INNER,
                    Communities.id,
                    Members.objectId
                ) {
                    Members.uid eq uid
                }.select(Communities.id, Members.joinedTime).where {
                    Communities.id.inList(communityIds)
                }
            }
            map {
                it[Communities.id] to it[Members.joinedTime]
            }
        }
    }

    override suspend fun getRawCommunities(
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
        map {
            val community = Community.wrapRow(it)
            val joinedTime = it.getOrNull(Members.joinedTime)
            val lastRead = it.getOrNull(UserTopicReads.topicId)
            val communityInfo = community
            RawCommunity(communityInfo, joinedTime, lastRead)
        }
    }

    override suspend fun updateCommunity(
        id: PrimaryKey,
        body: UpdateCommunityBody
    ) = databaseSession.dbQuery {
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

    override suspend fun getCommunityCount() = databaseSession.dbSearch {
        search {
            Communities.selectAll()
        }
        count()
    }
}
