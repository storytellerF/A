package com.storyteller_f.a.backend.exposed.database

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
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserDevice
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.isEmpty
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.*
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.AssetTransactions
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert

class ExposedUserDatabase(private val databaseSession: ExposedDatabaseSession) :
    UserDatabase {
    override suspend fun getUserAid(id: PrimaryKey) = databaseSession.dbSearch {
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
    ) = databaseSession.dbSearch {
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

    override suspend fun getRawUserAndPublicKeyByAddress(
        ad: String,
    ) = databaseSession.dbSearch {
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

    override suspend fun createUser(user: User) = databaseSession.dbQuery {
        createUserRaw(user)
    }

    private suspend fun createUserRaw(user: User) = check(Users.insert {
        it[Users.id] = user.id
        it[publicKey] = user.publicKey
        it[address] = user.address
        it[nickname] = user.nickname
        it[Users.createdTime] = user.createdTime
    }.insertedCount > 0) {
        "insert user failed"
    }

    override suspend fun isUserNotExistsByPublicKey(pk: String) = databaseSession.dbSearch {
        search {
            User.find {
                Users.publicKey eq pk
            }
        }
        isEmpty()
    }

    override suspend fun updateUserInfo(
        id: PrimaryKey,
        newUser: UpdateUserBody,
    ) = databaseSession.dbQuery {
        updateUser(newUser, id) && updateAid(newUser, id)
    }

    private suspend fun updateAid(
        newUser: UpdateUserBody,
        id: PrimaryKey
    ): Boolean {
        val aid = newUser.aid
        if (aid.isNullOrBlank()) {
            return true
        }
        return Aids.upsert(Aids.objectId) {
            it[objectId] = id
            it[value] = aid
            it[objectType] = ObjectType.USER
        }.insertedCount > 0
    }

    private suspend fun updateUser(
        newUser: UpdateUserBody,
        id: PrimaryKey
    ): Boolean {
        val avatar = newUser.avatar
        val name = newUser.nickname
        if (name.isNullOrBlank() && avatar == null) {
            return true
        }
        return Users.update({
            Users.id eq id
        }) {
            if (!name.isNullOrBlank()) {
                it[nickname] = name
            }
            if (avatar != null) {
                it[icon] = avatar
            }
        } > 0
    }

    private suspend fun getUserAuthDataBy(
        predicate: () -> Op<Boolean>,
    ) = databaseSession.dbSearch {
        search {
            Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

    override suspend fun getUserAuthDataById(id: PrimaryKey) = getUserAuthDataBy {
        Users.id eq id
    }

    override suspend fun getUserAuthDataByAid(aid: String) = databaseSession.dbSearch {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .select(listOf(Users.publicKey, Users.id))
                .where {
                    Aids.value eq aid
                }
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

    override suspend fun getUserAuthDataByAddress(address: String) = getUserAuthDataBy {
        Users.address eq address
    }

    override suspend fun getRawUsers(objectListFetch: ObjectListFetch) = databaseSession.dbSearch {
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

    override suspend fun getUserAcgByIds(objectListFetch: ObjectListFetch) =
        databaseSession.dbSearch {
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

    override suspend fun addReadLog(userTopicRead: UserTopicRead) = databaseSession.dbQuery {
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

    override suspend fun insertUserLog(log: UserLog) = databaseSession.dbQuery {
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

    override suspend fun addDevice(uid: PrimaryKey, endpointUrl: String) = databaseSession.dbQuery {
        check(UserDevices.insert {
            it[UserDevices.uid] = uid
            it[UserDevices.endpointUrl] = endpointUrl
        }.insertedCount > 0) {
            "Insert device failed"
        }
    }

    override suspend fun removeDevice(
        uid: PrimaryKey,
        endpointUrl: String,
    ) = databaseSession.dbQuery {
        UserDevices.deleteWhere {
            (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
        }
    }

    override suspend fun getUserDevices(uid: List<PrimaryKey>) = databaseSession.dbSearch {
        search {
            UserDevices.selectAll().where {
                UserDevices.uid inList uid
            }
        }
        map(UserDevice::wrapRow)
    }

    override suspend fun addAcgForUser(
        record: TaskRecord,
        assetTransactions: List<AssetTransaction>,
    ) = databaseSession.dbQuery {
        assetTransactions.forEach { at ->
            check(AssetTransactions.insert {
                it[AssetTransactions.id] = at.id
                it[AssetTransactions.uid] = at.uid
                it[AssetTransactions.createdTime] = at.createdTime
                it[AssetTransactions.type] = at.type
                it[AssetTransactions.before] = at.before
                it[AssetTransactions.after] = at.after
            }.insertedCount > 0) {
                "Insert asset transaction failed"
            }
            check(Users.update({
                Users.id eq at.uid and (Users.acgAmount eq at.before)
            }) {
                it[Users.acgAmount] = at.after
            } > 0) {
                "update user acg failed"
            }
        }

        TaskRecord.addTaskRecord(record)
    }

    override suspend fun getLatestTaskRecord(type: TaskRecordType) = databaseSession.dbSearch {
        search {
            TaskRecords.selectAll().where {
                TaskRecords.type eq type
            }.orderBy(TaskRecords.id, SortOrder.DESC)
        }
        first(TaskRecord::wrapRow)
    }

    override suspend fun getRawChildAccountPaginationListByHost(
        hostId: PrimaryKey,
        fetch: PrimaryKeyFetch,
    ) = databaseSession.dbSearch {
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
        databaseSession.dbSearch {
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

    override suspend fun getRawChildAccount(uid: PrimaryKey) = databaseSession.dbSearch {
        search {
            ChildAccounts.selectAll().where {
                ChildAccounts.uid eq uid
            }
        }
        first {
            ChildAccount.wrapRow(it)
        }
    }

    override suspend fun createChildAccount(
        hostId: PrimaryKey,
        privateKey: String,
        user: User
    ) = databaseSession.dbQuery {
        createUserRaw(user)
        check(ChildAccounts.insert {
            it[this.hostId] = hostId
            it[this.privateKey] = privateKey
            it[uid] = user.id
        }.insertedCount > 0) {
            "Insert alternate account failed"
        }
    }

    override suspend fun getAllUsers(primaryKeyFetch: PrimaryKeyFetch) = runCatching {
        val r1 = databaseSession.dbSearch {
            search {
                Users
                    .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .select(Users.fields + Aids.value)
                    .bindPaginationQuery(Users, primaryKeyFetch)
            }
            map(::mapUserInfo)
        }.getOrThrow()
        val r2 = databaseSession.dbSearch {
            search {
                Users.selectAll()
            }
            count()
        }.getOrThrow()
        PaginationResult(r1, r2)
    }

    override suspend fun getUserCount() = databaseSession.dbSearch {
        search {
            Users.selectAll()
        }
        count()
    }
}
