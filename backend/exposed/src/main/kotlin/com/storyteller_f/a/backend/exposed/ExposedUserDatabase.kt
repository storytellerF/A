package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.UserDatabase
import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.ChildAccount
import com.storyteller_f.a.backend.core.types.RawChildAccount
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserDevice
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.a.backend.exposed.tables.AssetTransactions
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert

class ExposedUserDatabase(private val exposedDatabaseSession: ExposedDatabaseSession) :
    UserDatabase {
    override suspend fun getUserAid(id: PrimaryKey) = exposedDatabaseSession.dbSearch {
        search {
            Aids.selectAll().where {
                Aids.objectId eq id
            }
        }
        first {
            it[Aids.value]
        }
    }

    override suspend fun getRawUser(
        objectFetch: ObjectFetch,
    ): Result<RawUser?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
                    when (objectFetch) {
                        is ObjectFetch.AidFetch -> Aids.value eq objectFetch.aid
                        is ObjectFetch.IdFetch -> Users.id eq objectFetch.id
                    }
                }
            }
            first(::mapUserInfo)
        }
    }

    override suspend fun getRawUserAndPublicKeyByAddress(
        ad: String,
    ): Result<Pair<RawUser, String>?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users
                    .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .select(Users.fields + Aids.value)
                    .where {
                        Users.address eq ad
                    }
            }
            first {
                val value = User.wrapRow(it)
                Pair(RawUser(value), value.publicKey)
            }
        }
    }

    override suspend fun createUser(
        user: User,
    ): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            createUserRaw(user)
        }
    }

    private suspend fun createUserRaw(user: User) {
        check(Users.insert {
            it[Users.id] = user.id
            it[publicKey] = user.publicKey
            it[address] = user.address
            it[nickname] = user.nickname
            it[Users.createdTime] = user.createdTime
        }.insertedCount > 0) {
            "insert user failed"
        }
    }

    override suspend fun isUserNotExistsByPublicKey(pk: String): Result<Boolean> {
        return exposedDatabaseSession.dbSearch {
            search {
                User.find {
                    Users.publicKey eq pk
                }
            }
            isEmpty()
        }
    }

    override suspend fun updateUserInfo(
        id: PrimaryKey,
        newUser: UpdateUserBody,
    ): Result<Boolean> {
        return exposedDatabaseSession.dbQuery {
            listOf(suspend {
                val avatar = newUser.avatar
                val name = newUser.nickname
                if (!name.isNullOrBlank() || avatar != null) {
                    Users.update({
                        Users.id eq id
                    }) {
                        if (!name.isNullOrBlank()) {
                            it[nickname] = name
                        }
                        if (avatar != null) {
                            it[icon] = avatar
                        }
                    } > 0
                } else {
                    true
                }
            }, {
                val aid = newUser.aid
                if (!aid.isNullOrBlank()) {
                    Aids.upsert(Aids.objectId) {
                        it[objectId] = id
                        it[value] = aid
                        it[objectType] = ObjectType.USER
                    }.insertedCount > 0
                } else {
                    true
                }
            }).all {
                it()
            }
        }
    }

    override suspend fun isUserExistsByUid(id: Long): Result<Boolean> {
        return exposedDatabaseSession.dbSearch {
            search {
                User.find {
                    Users.id eq id
                }
            }
            isNotEmpty()
        }
    }

    suspend fun getUserAuthDataByAid(
        predicate: SqlExpressionBuilder.() -> Op<Boolean>,
    ): Result<Pair<String, Long>?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .select(listOf(Users.publicKey, Users.id))
                    .where(predicate)
            }
            first {
                it[Users.publicKey] to it[Users.id]
            }
        }
    }

    suspend fun getUserAuthDataBy(
        predicate: SqlExpressionBuilder.() -> Op<Boolean>,
    ): Result<Pair<String, Long>?> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
            }
            first {
                it[Users.publicKey] to it[Users.id]
            }
        }
    }

    override suspend fun getUserAuthDataById(id: PrimaryKey): Result<Pair<String, Long>?> {
        return getUserAuthDataBy {
            Users.id eq id
        }
    }

    override suspend fun getUserAuthDataByAid(aid: String): Result<Pair<String, Long>?> {
        return getUserAuthDataByAid {
            Aids.value eq aid
        }
    }

    override suspend fun getUserAuthDataByAddress(address: String): Result<Pair<String, Long>?> {
        return getUserAuthDataBy {
            Users.address eq address
        }
    }

    override suspend fun getRawUsers(objectListFetch: ObjectListFetch): Result<List<RawUser>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users
                    .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .select(Users.fields + Aids.value)
                    .where {
                        when (objectListFetch) {
                            is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                            is ObjectListFetch.IdListFetch -> Users.id inList objectListFetch.idList
                        }
                    }
            }
            map(::mapUserInfo)
        }
    }

    override suspend fun getUserAcgByIds(objectListFetch: ObjectListFetch): Result<List<Pair<Long, Long>>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users.select(Users.fields)
                    .where {
                        when (objectListFetch) {
                            is ObjectListFetch.AidListFetch -> Aids.value inList objectListFetch.aidList
                            is ObjectListFetch.IdListFetch -> Users.id inList objectListFetch.idList
                        }
                    }
            }
            map {
                it[Users.id] to it[Users.acgAmount]
            }
        }
    }

    override suspend fun addReadLog(userTopicRead: UserTopicRead): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            check(UserTopicReads.upsert {
                it[uid] = userTopicRead.uid
                it[updatedAt] = userTopicRead.updatedAt
                it[objectId] = userTopicRead.objectId
                it[objectType] = userTopicRead.objectType
                it[topicId] = userTopicRead.topicId
            }.insertedCount > 0) {
                "log failed"
            }
        }
    }

    override suspend fun insertUserLog(log: UserLog): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            check(UserLogs.insert {
                it[UserLogs.id] = log.id
                it[uid] = log.uid
                it[type] = log.type
                it[objectId] = log.objectId
                it[objectType] = log.objectType
                it[UserLogs.createdTime] = log.createdTime
            }.insertedCount > 0) {
                "Insert user log failed"
            }
        }
    }

    override suspend fun addDevice(uid: PrimaryKey, endpointUrl: String): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            check(UserDevices.insert {
                it[UserDevices.uid] = uid
                it[UserDevices.endpointUrl] = endpointUrl
            }.insertedCount > 0) {
                "Insert device failed"
            }
        }
    }

    override suspend fun removeDevice(
        uid: PrimaryKey,
        endpointUrl: String,
    ): Result<Int> {
        return exposedDatabaseSession.dbQuery {
            UserDevices.deleteWhere {
                (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
            }
        }
    }

    override suspend fun getUserDevices(uid: List<PrimaryKey>): Result<List<UserDevice>> {
        return exposedDatabaseSession.dbSearch {
            search {
                UserDevices.selectAll().where {
                    UserDevices.uid inList uid
                }
            }
            map(UserDevice::wrapRow)
        }
    }

    override suspend fun addAcgForUser(
        acgList: List<Pair<PrimaryKey, Int>>,
        userAcgMap: Map<Long, Long>,
        list: List<Topic>,
        taskRecordId: PrimaryKey,
    ): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            acgList.forEach { (id, acg) ->
                userAcgMap[id]?.let { oldAcgAmount ->
                    Users.update({
                        Users.id eq id
                    }) {
                        it[acgAmount] = oldAcgAmount + acg
                    }
                    val assetTransaction = AssetTransaction(
                        AssetType.ACG,
                        oldAcgAmount,
                        oldAcgAmount + acg
                    )
                    check(AssetTransactions.insert {
                        it[AssetTransactions.type] = assetTransaction.type
                        it[AssetTransactions.before] = assetTransaction.before
                        it[AssetTransactions.after] = assetTransaction.after
                    }.insertedCount > 0) {
                        "Insert asset transaction failed"
                    }
                }
            }

            TaskRecord.addTaskRecord(
                TaskRecord(
                    taskRecordId,
                    now(),
                    TaskRecordType.TOPIC_ACG,
                    list.last().id
                )
            )
        }
    }

    override suspend fun getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?> {
        return exposedDatabaseSession.dbSearch {
            search {
                TaskRecords.selectAll().where {
                    TaskRecords.type eq type
                }.orderBy(TaskRecords.id, SortOrder.DESC)
            }
            first(TaskRecord::wrapRow)
        }
    }

    override suspend fun getRawChildAccountPaginationListByHost(
        hostId: PrimaryKey,
        fetch: PrimaryKeyFetch,
    ): Result<PaginationResult<RawChildAccount>> {
        return exposedDatabaseSession.dbSearch {
            search {
                Users.join(ChildAccounts, JoinType.INNER, Users.id, ChildAccounts.uid)
                    .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .select(Users.fields + ChildAccounts.fields + Aids.value).where {
                        ChildAccounts.hostId eq hostId
                    }.bindPaginationQuery(Users, fetch)
            }
            map {
                val childAccount = ChildAccount.wrapRow(it)
                val user = User.wrapRow(it)
                RawChildAccount(childAccount, RawUser(user))
            }
        }.mapResult { list ->
            exposedDatabaseSession.dbSearch {
                search {
                    ChildAccounts.join(Users, JoinType.INNER, ChildAccounts.uid, Users.id)
                        .selectAll()
                        .where {
                            ChildAccounts.hostId eq hostId
                        }
                }
                count()
            }.map { count ->
                PaginationResult(list, count)
            }
        }
    }

    override suspend fun getRawChildAccount(uid: PrimaryKey): Result<ChildAccount?> {
        return exposedDatabaseSession.dbSearch {
            search {
                ChildAccounts.selectAll().where {
                    ChildAccounts.uid eq uid
                }
            }
            first {
                ChildAccount.wrapRow(it)
            }
        }
    }

    override suspend fun createChildAccount(
        hostId: PrimaryKey,
        privateKey: String,
        user: User
    ): Result<Unit> {
        return exposedDatabaseSession.dbQuery {
            createUserRaw(user)
            check(ChildAccounts.insert {
                it[this.hostId] = hostId
                it[this.privateKey] = privateKey
                it[uid] = user.id
            }.insertedCount > 0) {
                "Insert alternate account failed"
            }
        }
    }
}
