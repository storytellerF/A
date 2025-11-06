package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.ContainerInfo
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.isNotEmpty
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildSearchMembersQuery
import com.storyteller_f.a.backend.exposed.tables.Members
import com.storyteller_f.a.backend.exposed.tables.Quotas
import com.storyteller_f.a.backend.exposed.tables.Topics
import com.storyteller_f.a.backend.exposed.tables.UserTopicReads
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.a.backend.exposed.tables.addJoin
import com.storyteller_f.a.backend.exposed.tables.mapUserInfo
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll

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

    override suspend fun joinContainer(member: Member): Result<Unit> = databaseSession.dbQuery {
        addJoin(member)
    }

    override suspend fun exitContainer(
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
    ) = databaseSession.dbSearch {
        search {
            buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                Users,
                fetch
            )
        }
        map(::mapUserInfo)
    }.mapResult { results ->
        databaseSession.dbSearch {
            search {
                buildSearchMembersQuery(objectId, true, word)
            }
            count()
        }.map { value ->
            PaginationResult(results, value)
        }
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
            it[locking] = false
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
}
