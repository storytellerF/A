package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.ContainerDatabase
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.MemberJoin
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildSearchMembersQuery
import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.merge
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll

class ExposedContainerDatabase(val exposedDatabaseSession: ExposedDatabaseSession) :
    ContainerDatabase {
    override suspend fun isMemberJoined(
        objectId: PrimaryKey,
        uid: PrimaryKey?,
    ): Result<Boolean> {
        return if (uid == null) {
            Result.success(false)
        } else {
            exposedDatabaseSession.dbSearch {
                search {
                    MemberJoins.selectAll().where {
                        (MemberJoins.objectId eq objectId) and (MemberJoins.uid eq uid)
                    }
                }
                isNotEmpty()
            }
        }
    }

    override suspend fun joinContainer(
        id: PrimaryKey,
        uid: PrimaryKey,
        time: LocalDateTime,
        objectType: ObjectType,
    ): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            MemberJoin.addJoinRaw(uid, id, time, objectType)
        }
    }

    override suspend fun exit(
        containerId: PrimaryKey,
        id: PrimaryKey,
    ): Result<Int> {
        return exposedDatabaseSession.dbQuery {
            MemberJoins.deleteWhere {
                objectId eq containerId and (uid eq id)
            }
        }
    }

    override suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<MemberJoin>> {
        return exposedDatabaseSession.dbSearch {
            search {
                MemberJoins.selectAll().where {
                    MemberJoins.objectId eq roomId
                }
            }
            map(MemberJoin::wrapRow)
        }
    }

    override suspend fun getUserJoinedTime(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey,
    ): Result<List<MemberJoin>> {
        return exposedDatabaseSession.dbSearch {
            search {
                MemberJoins.select(MemberJoins.fields).where {
                    (MemberJoins.uid eq uid) and (MemberJoins.objectId inList parentIds)
                }
            }
            map(MemberJoin::wrapRow)
        }
    }

    override suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>> {
        return exposedDatabaseSession.dbSearch {
            val column = MemberJoins.uid.countDistinct()
            search {
                MemberJoins.select(MemberJoins.objectId, column).where {
                    MemberJoins.objectId inList parentIds
                }.groupBy(MemberJoins.objectId)
            }
            map {
                it[MemberJoins.objectId] to it[column]
            }
        }
    }

    override suspend fun getContainerInfo(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey?,
    ): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>> {
        return merge({
            if (uid != null && parentIds.isNotEmpty()) {
                getUserJoinedTime(parentIds, uid).map {
                    it.associateBy { memberJoin ->
                        memberJoin.objectId
                    }
                }
            } else {
                Result.success(emptyMap())
            }
        }, {
            if (uid != null && parentIds.isNotEmpty()) {
                getTopicReadList(parentIds, uid).map {
                    it.associateBy { userTopicRead ->
                        userTopicRead.objectId
                    }
                }
            } else {
                Result.success(emptyMap())
            }
        }, {
            if (parentIds.isNotEmpty()) {
                getMemberCount(parentIds).map {
                    it.associateByPair()
                }
            } else {
                Result.success(emptyMap())
            }
        })
    }

    override suspend fun getTopicReadList(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey,
    ): Result<List<UserTopicRead>> {
        return exposedDatabaseSession.dbSearch {
            search {
                UserTopicReads.selectAll().where {
                    UserTopicReads.uid eq uid and (UserTopicReads.objectId inList parentIds)
                }
            }
            map(UserTopicRead::wrapRow)
        }
    }

    override suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawUser>> {
        return exposedDatabaseSession.dbSearch {
            search {
                buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                    Users,
                    fetch
                )
            }
            map(::mapUserInfo)
        }.mapResult { results ->
            exposedDatabaseSession.dbSearch {
                search {
                    buildSearchMembersQuery(objectId, true, word)
                }
                count()
            }.map { value ->
                PaginationResult(results, value)
            }
        }
    }
}
