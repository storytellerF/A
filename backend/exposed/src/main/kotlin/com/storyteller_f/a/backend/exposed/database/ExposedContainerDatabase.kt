package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.ContainerInfo
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.paginationFromResults
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.isNotEmpty
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.Members
import com.storyteller_f.a.backend.exposed.tables.Quotas
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.backend.exposed.tables.UserTopicReads
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.a.backend.exposed.tables.mapUserInfo
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.NestedMemberInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedContainerDatabase(val databaseSession: ExposedDatabaseSession) :
    ContainerDatabase {
    override suspend fun isMemberJoined(
        objectId: PrimaryKey,
        uid: PrimaryKey?,
    ): Result<Boolean> {
        if (uid == null) {
            return Result.success(false)
        }
        return databaseSession.dbSearch {
            search {
                Members.selectAll().where {
                    (Members.objectId eq objectId) and (Members.uid eq uid)
                }
            }
            isNotEmpty()
        }
    }

    override suspend fun addMember(member: Member) = databaseSession.dbQuery {
        check(Members.insert {
            it[id] = member.id
            it[createdTime] = member.createdTime
            it[joinedTime] = member.joinedTime
            it[invitedTime] = member.invitedTime
            it[uid] = member.uid
            it[objectId] = member.objectId
            it[objectType] = member.objectType
            it[status] = member.status
        }.insertedCount > 0) {
            "join failed"
        }
        member
    }

    override suspend fun updateMemberStatus(member: Member) = databaseSession.dbQuery {
        check(Members.update(where = {
            Members.id eq member.id
        }) {
            it[joinedTime] = member.joinedTime
            it[invitedTime] = member.invitedTime
            it[status] = member.status
        } > 0) {
            "update member failed"
        }
        member
    }

    override suspend fun deleteMember(
        containerId: PrimaryKey,
        id: PrimaryKey,
    ) = databaseSession.dbQuery {
        check(Members.deleteWhere {
            objectId eq containerId and (uid eq id)
        } > 0) {
            "delete member failed"
        }
    }

    override suspend fun getJoinedUserList(roomId: PrimaryKey) = databaseSession.dbSearch {
        search {
            Members.selectAll().where {
                Members.objectId eq roomId
            }
        }
        map(Member::wrapRow)
    }

    override suspend fun getUserJoinedTime(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey,
    ) = databaseSession.dbSearch {
        search {
            Members.select(Members.fields).where {
                (Members.uid eq uid) and (Members.objectId inList parentIds)
            }
        }
        map(Member::wrapRow)
    }

    override suspend fun getMemberCount(parentIds: List<PrimaryKey>) = databaseSession.dbSearch {
        val column = Members.uid.countDistinct()
        search {
            Members.select(Members.objectId, column).where {
                Members.objectId inList parentIds
            }.groupBy(Members.objectId)
        }
        map {
            it[Members.objectId] to it[column]
        }
    }

    override suspend fun getContainerInfo(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?,
    ): Result<Map<PrimaryKey, ContainerInfo>> {
        if (parentIds.isEmpty()) return Result.success(emptyMap())
        return runCatching {
            val joinMap = if (uid != null && parentIds.isNotEmpty()) {
                getUserJoinedTime(parentIds, uid).map {
                    it.associateBy { memberJoin ->
                        memberJoin.objectId
                    }
                }
            } else {
                Result.success(emptyMap())
            }.getOrThrow()
            val readMap = if (uid != null && parentIds.isNotEmpty()) {
                getTopicReadList(parentIds, uid).map {
                    it.associateBy { userTopicRead ->
                        userTopicRead.objectId
                    }
                }
            } else {
                Result.success(emptyMap())
            }.getOrThrow()
            val memberCountMap = if (parentIds.isNotEmpty()) {
                getMemberCount(parentIds).map {
                    it.associateByPair()
                }
            } else {
                Result.success(emptyMap())
            }.getOrThrow()
            val latestMap = getLatestTopicInContainer(parentIds, uid).getOrThrow()
            parentIds.associateWith {
                ContainerInfo(
                    joinMap[it],
                    readMap[it],
                    memberCountMap[it],
                    latestMap[it]
                )
            }
        }
    }

    override suspend fun getTopicReadList(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey,
    ) = databaseSession.dbSearch {
        search {
            UserTopicReads.selectAll().where {
                UserTopicReads.uid eq uid and (UserTopicReads.objectId inList parentIds)
            }
        }
        map(UserTopicRead::wrapRow)
    }

    override suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        fetch: PrimaryKeyFetch,
    ) = paginationFromResults(
        databaseSession.dbSearch {
            search {
                buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                    Users,
                    fetch
                )
            }
            map(::mapUserInfo)
        },
        databaseSession.dbSearch {
            search {
                buildSearchMembersQuery(objectId, true, word)
            }
            count()
        }
    )

    override suspend fun getMemberWithUserPaginationResult(
        objectId: PrimaryKey,
        fetch: PrimaryKeyFetch
    ) = paginationFromResults(
        databaseSession.dbSearch {
            search {
                Users
                    .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .join(Members, JoinType.INNER, Users.id, Members.uid) {
                        Members.objectId eq objectId
                    }
                    .select(Users.fields + Aids.value + Members.fields)
                    .bindPaginationQuery(Users, fetch)
            }
            map { row -> Pair(Member.wrapRow(row), mapUserInfo(row)) }
        },
        databaseSession.dbSearch {
            search {
                Members.selectAll().where { Members.objectId eq objectId }
            }
            count()
        }
    )

    override suspend fun getMemberWithUserByUids(
        objectId: PrimaryKey,
        uidList: List<PrimaryKey>
    ) = databaseSession.dbSearch {
        search {
            Users
                .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .join(Members, JoinType.INNER, Users.id, Members.uid) {
                    (Members.objectId eq objectId) and (Members.uid inList uidList)
                }
                .select(Users.fields + Aids.value + Members.fields)
        }
        map { row -> Pair(Member.wrapRow(row), mapUserInfo(row)) }
    }

    override suspend fun getQuotaInfo(
        ownerId: PrimaryKey,
        quotaType: QuotaType
    ) = databaseSession.dbSearch {
        search {
            Quotas.selectAll().where {
                Quotas.ownerId eq ownerId and (Quotas.quotaType eq quotaType)
            }
        }
        first {
            Quota.wrapRow(it)
        }
    }

    override suspend fun insertQuota(quota: Quota) = databaseSession.dbQuery {
        check(Quotas.insert {
            it[ownerId] = quota.ownerId
            it[ownerType] = quota.ownerType
            it[total] = quota.total
            it[used] = quota.used
            it[quotaType] = quota.quotaType
            it[lockId] = null
        }.insertedCount > 0) {
            "insert quota failed"
        }
    }

    override suspend fun getLatestTopicInContainer(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?
    ) = databaseSession.dbSearch {
        val maxColumn = Topics.id.max()
        search {
            Topics.select(maxColumn, Topics.parentId).where {
                Topics.parentId inList parentIds
            }.groupBy(Topics.parentId)
        }
        map {
            it[Topics.parentId] to it[maxColumn]
        }
    }.map {
        it.associateByPair()
    }

    override suspend fun getMember(
        containerId: PrimaryKey,
        id: PrimaryKey
    ) = databaseSession.dbSearch {
        search {
            Members.selectAll().where {
                Members.objectId eq containerId and (Members.uid eq id)
            }
        }
        first {
            Member.wrapRow(it)
        }
    }

    override suspend fun getMemberByIds(
        uid: PrimaryKey,
        objectIds: List<PrimaryKey>
    ): Result<List<Pair<Long, NestedMemberInfo?>>> {
        if (objectIds.isEmpty()) return Result.success(emptyList())
        return databaseSession.dbSearch {
            search {
                Members.selectAll().where {
                    Members.objectId inList objectIds and (Members.uid eq uid)
                }
            }
            map {
                val member = Member.wrapRow(it)
                it[Members.objectId] to NestedMemberInfo(
                    member.status,
                    member.joinedTime,
                    member.invitedTime
                )
            }
        }
    }

    fun buildSearchMembersQuery(objectId: PrimaryKey?, getCount: Boolean, word: String?): Query {
        val query = if (objectId != null) {
            val join = Users
                .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .join(Members, JoinType.INNER, Users.id, Members.uid) {
                    Members.objectId eq objectId
                }
            if (getCount) {
                join.selectAll()
            } else {
                join.select(Users.fields + Members.joinedTime + Aids.value)
            }
        } else {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).select(Users.fields + Aids.value)
        }

        if (!word.isNullOrBlank()) {
            query.andWhere {
                Users.nickname like "%$word%"
            }
        }
        return query
    }
}
