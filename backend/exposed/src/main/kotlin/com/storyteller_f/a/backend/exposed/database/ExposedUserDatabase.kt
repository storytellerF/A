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
import com.storyteller_f.a.backend.core.types.SubscriptionSentLog
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.UserDevice
import com.storyteller_f.a.backend.core.types.UserFavorite
import com.storyteller_f.a.backend.core.types.UserLog
import com.storyteller_f.a.backend.core.types.UserSubscription
import com.storyteller_f.a.backend.core.types.UserTopicRead
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.first
import com.storyteller_f.a.backend.exposed.isEmpty
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.Aids
import com.storyteller_f.a.backend.exposed.tables.AssetTransactions
import com.storyteller_f.a.backend.exposed.tables.ChildAccounts
import com.storyteller_f.a.backend.exposed.tables.SubscriptionSentLogs
import com.storyteller_f.a.backend.exposed.tables.TaskRecords
import com.storyteller_f.a.backend.exposed.tables.UserDevices
import com.storyteller_f.a.backend.exposed.tables.UserFavorites
import com.storyteller_f.a.backend.exposed.tables.UserLogs
import com.storyteller_f.a.backend.exposed.tables.UserSubscriptions
import com.storyteller_f.a.backend.exposed.tables.UserTopicReads
import com.storyteller_f.a.backend.exposed.tables.Users
import com.storyteller_f.a.backend.exposed.tables.addTaskRecord
import com.storyteller_f.a.backend.exposed.tables.find
import com.storyteller_f.a.backend.exposed.tables.mapUserInfo
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.model.UserOverview
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
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

    override suspend fun createUser(user: User): Result<User> = databaseSession.dbQuery {
        createUserRaw(user)
        user
    }

    private suspend fun createUserRaw(user: User) = check(Users.insert {
        it[id] = user.id
        it[publicKey] = user.publicKey
        it[address] = user.address
        it[nickname] = user.nickname
        it[createdTime] = user.createdTime
        it[notificationId] = user.notificationId
        it[passType] = user.passType
        it[algoType] = user.algoType
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

    override suspend fun getRawUsers(objectListFetch: ObjectListFetch): Result<List<RawUser>> {
        if (objectListFetch is ObjectListFetch.AidListFetch && objectListFetch.aidList.isEmpty()) {
            return Result.success(emptyList())
        }
        if (objectListFetch is ObjectListFetch.IdListFetch && objectListFetch.idList.isEmpty()) {
            return Result.success(emptyList())
        }
        return databaseSession.dbSearch {
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
        check(UserTopicReads.upsert(onUpdate = {
            it[UserTopicReads.updatedAt] = userTopicRead.updatedAt
            it[UserTopicReads.topicId] = userTopicRead.topicId
        }) {
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

        addTaskRecord(record)
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
    ) = runCatching {
        val childAccounts = databaseSession.dbSearch {
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
        }.getOrThrow()
        val total = getChildAccountCount(hostId)
        PaginationResult(childAccounts, total)
    }

    private suspend fun getChildAccountCount(hostId: PrimaryKey): Long {
        val total = databaseSession.dbSearch {
            search {
                ChildAccounts.join(Users, JoinType.INNER, ChildAccounts.uid, Users.id)
                    .selectAll()
                    .where {
                        ChildAccounts.hostId eq hostId
                    }
            }
            count()
        }.getOrThrow()
        return total
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
        val rawUsers = databaseSession.dbSearch {
            search {
                Users
                    .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                    .select(Users.fields + Aids.value)
                    .bindPaginationQuery(Users, primaryKeyFetch)
            }
            map(::mapUserInfo)
        }.getOrThrow()
        val total = databaseSession.dbSearch {
            search {
                Users.selectAll()
            }
            count()
        }.getOrThrow()
        PaginationResult(rawUsers, total)
    }

    override suspend fun getUserCount() = databaseSession.dbSearch {
        search {
            Users.selectAll()
        }
        count()
    }

    override suspend fun getUserFavorites(uid: PrimaryKey, fetch: PrimaryKeyFetch) = runCatching {
        val userFavorites = databaseSession.dbSearch {
            search {
                UserFavorites.selectAll().where {
                    UserFavorites.uid eq uid
                }.bindPaginationQuery(UserFavorites, fetch)
            }
            map {
                UserFavorite.wrapRow(it)
            }
        }.getOrThrow()
        val total = getUserFavoriteCount()
        PaginationResult(userFavorites, total)
    }

    private suspend fun getUserFavoriteCount() = databaseSession.dbSearch {
        search {
            UserFavorites.selectAll()
        }
        count()
    }.getOrThrow()

    override suspend fun addFavorite(userFavorite: UserFavorite) = databaseSession.dbQuery {
        check(UserFavorites.insert {
            it[id] = userFavorite.id
            it[uid] = userFavorite.uid
            it[objectId] = userFavorite.objectId
            it[objectType] = userFavorite.objectType
            it[createdTime] = userFavorite.createdTime
        }.insertedCount > 0) {
            "Insert favorite failed"
        }
        userFavorite
    }

    override suspend fun removeFavorite(id: PrimaryKey) = databaseSession.dbQuery {
        check(UserFavorites.deleteWhere {
            UserFavorites.id eq id
        } > 0) {
            "Remove favorite failed"
        }
    }

    override suspend fun getFavorite(id: PrimaryKey) = databaseSession.dbSearch {
        search {
            UserFavorites.selectAll().where {
                UserFavorites.id eq id
            }
        }
        first {
            UserFavorite.wrapRow(it)
        }
    }

    override suspend fun getFavorite(
        uid: PrimaryKey,
        objectId: PrimaryKey
    ): Result<UserFavorite?> {
        return databaseSession.dbSearch {
            search {
                UserFavorites.selectAll().where {
                    (UserFavorites.uid eq uid) and (UserFavorites.objectId eq objectId)
                }
            }
            first {
                UserFavorite.wrapRow(it)
            }
        }
    }

    override suspend fun getUserSubscriptions(
        uid: PrimaryKey,
        fetch: PrimaryKeyFetch
    ) = runCatching {
        val userSubscriptions = databaseSession.dbSearch {
            search {
                UserSubscriptions.selectAll().where {
                    UserSubscriptions.uid eq uid
                }.bindPaginationQuery(UserSubscriptions, fetch)
            }
            map {
                UserSubscription.wrapRow(it)
            }
        }.getOrThrow()
        val total = getUserSubscriptionCount(uid)
        PaginationResult(userSubscriptions, total)
    }

    private suspend fun getUserSubscriptionCount(uid: PrimaryKey) = databaseSession.dbSearch {
        search {
            UserSubscriptions.selectAll().where {
                UserSubscriptions.uid eq uid
            }
        }
        count()
    }.getOrThrow()

    override suspend fun addSubscription(userSubscription: UserSubscription) =
        databaseSession.dbQuery {
            check(UserSubscriptions.insert {
                it[id] = userSubscription.id
                it[uid] = userSubscription.uid
                it[objectId] = userSubscription.objectId
                it[objectType] = userSubscription.objectType
                it[createdTime] = userSubscription.createdTime
            }.insertedCount > 0) {
                "Insert subscription failed"
            }
            userSubscription
        }

    override suspend fun removeSubscription(id: PrimaryKey) = databaseSession.dbQuery {
        check(UserSubscriptions.deleteWhere {
            UserSubscriptions.id eq id
        } > 0) {
            "Remove subscription failed"
        }
    }

    override suspend fun getSubscription(id: PrimaryKey) = databaseSession.dbSearch {
        search {
            UserSubscriptions.selectAll().where {
                UserSubscriptions.id eq id
            }
        }
        first {
            UserSubscription.wrapRow(it)
        }
    }

    override suspend fun getSubscription(
        uid: PrimaryKey,
        objectId: PrimaryKey
    ): Result<UserSubscription?> {
        return databaseSession.dbSearch {
            search {
                UserSubscriptions.selectAll().where {
                    (UserSubscriptions.uid eq uid) and (UserSubscriptions.objectId eq objectId)
                }
            }
            first {
                UserSubscription.wrapRow(it)
            }
        }
    }

    override suspend fun getUserOverview(uid: PrimaryKey): Result<UserOverview> {
        return runCatching {
            val subscriptionCount = getUserSubscriptionCount(uid)
            val favoriteCount = getUserFavoriteCount()
            val childAccountCount = getChildAccountCount(uid)
            UserOverview(subscriptionCount, favoriteCount, 0, childAccountCount)
        }
    }

    override suspend fun getSubscriptionsByObjectId(
        objectId: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<List<UserSubscription>> {
        return databaseSession.dbSearch {
            search {
                UserSubscriptions.selectAll().where {
                    UserSubscriptions.objectId eq objectId
                }.bindPaginationQuery(UserSubscriptions, primaryKeyFetch)
            }
            map {
                UserSubscription.wrapRow(it)
            }
        }
    }

    override suspend fun insertSubscriptionSentLog(log: SubscriptionSentLog): Result<SubscriptionSentLog> {
        return databaseSession.dbQuery {
            check(SubscriptionSentLogs.insert {
                it[id] = log.id
                it[uid] = log.uid
                it[objectId] = log.objectId
                it[objectType] = log.objectType
                it[createdTime] = log.createdTime
                it[subscriptionId] = log.subscriptionId
            }.insertedCount > 0) {
                "Insert subscription sent log failed"
            }
            log
        }
    }

    override suspend fun getLatestSubscriptionSentLog(objectId: PrimaryKey): Result<SubscriptionSentLog?> {
        return databaseSession.dbSearch {
            search {
                SubscriptionSentLogs.selectAll().where {
                    SubscriptionSentLogs.objectId eq objectId
                }.orderBy(SubscriptionSentLogs.subscriptionId to SortOrder.DESC)
            }
            first {
                SubscriptionSentLog.wrapRow(it)
            }
        }
    }
}
