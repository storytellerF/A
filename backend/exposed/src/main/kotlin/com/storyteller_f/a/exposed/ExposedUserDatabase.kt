package com.storyteller_f.a.exposed

import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.a.exposed.query.bindPaginationQuery
import com.storyteller_f.a.exposed.query.buildSearchMembersQuery
import com.storyteller_f.a.exposed.tables.Aids
import com.storyteller_f.a.exposed.tables.AlternateAccounts
import com.storyteller_f.a.exposed.tables.AssetTransaction
import com.storyteller_f.a.exposed.tables.Media
import com.storyteller_f.a.exposed.tables.MediaRefs
import com.storyteller_f.a.exposed.tables.Medias
import com.storyteller_f.a.exposed.tables.MemberJoin
import com.storyteller_f.a.exposed.tables.MemberJoins
import com.storyteller_f.a.exposed.tables.TaskRecord
import com.storyteller_f.a.exposed.tables.TaskRecords
import com.storyteller_f.a.exposed.tables.Topic
import com.storyteller_f.a.exposed.tables.User
import com.storyteller_f.a.exposed.tables.UserDevice
import com.storyteller_f.a.exposed.tables.UserDevices
import com.storyteller_f.a.exposed.tables.UserLog
import com.storyteller_f.a.exposed.tables.UserLogs
import com.storyteller_f.a.exposed.tables.UserRawResult
import com.storyteller_f.a.exposed.tables.UserTopicRead
import com.storyteller_f.a.exposed.tables.UserTopicReads
import com.storyteller_f.a.exposed.tables.Users
import com.storyteller_f.a.exposed.tables.mapUserInfo
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.merge
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

class ExposedUserDatabase(val exposedUserDatabase: ExposedDatabaseSession) : UserDatabase {
    override suspend fun getUserAid(id: PrimaryKey) = exposedUserDatabase.dbSearch {
        search {
            Aids.selectAll().where {
                Aids.objectId eq id
            }
        }
        first {
            it[Aids.value]
        }
    }

    override suspend fun getUserRawResult(
        objectFetch: ObjectFetch
    ): Result<UserRawResult?> {
        return exposedUserDatabase.dbSearch {
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

    override suspend fun getMemberPaginationResult(
        objectId: PrimaryKey?,
        word: String?,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<PaginationResult<UserRawResult>> {
        return exposedUserDatabase.dbSearch {
            search {
                buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                    Users,
                    primaryKeyFetch
                )
            }
            map(::mapUserInfo)
        }.mapResult { results ->
            exposedUserDatabase.dbSearch {
                search {
                    buildSearchMembersQuery(objectId, true, word)
                }
                count()
            }.map { value ->
                PaginationResult(results, value)
            }
        }
    }

    override suspend fun getUserRawResultAndPublicKeyByAddress(
        ad: String
    ): Result<Pair<UserRawResult, String>?> {
        return exposedUserDatabase.dbSearch {
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
                Pair(UserRawResult(value), value.publicKey)
            }
        }
    }

    override suspend fun createUser(
        user: User
    ): Result<Unit> {
        return exposedUserDatabase.dbQuery {
            check(Users.insert {
                it[Users.id] = user.id
                it[Users.publicKey] = user.publicKey
                it[Users.address] = user.address
                it[Users.nickname] = user.nickname
                it[Users.createdTime] = user.createdTime
            }.insertedCount > 0) {
                "insert user failed"
            }
        }
    }

    override suspend fun isUserNotExists(pk: String): Result<Boolean> {
        return exposedUserDatabase.dbSearch {
            search {
                User.Companion.find {
                    Users.publicKey eq pk
                }
            }
            isEmpty()
        }
    }

    override suspend fun updateUserInfo(
        id: PrimaryKey,
        newUser: UpdateUserBody
    ): Result<Boolean> {
        return exposedUserDatabase.dbQuery {
            listOf({
                val avatar = newUser.avatar
                val name = newUser.nickname
                if (!name.isNullOrBlank() || avatar != null) {
                    Users.update({
                        Users.id eq id
                    }) {
                        if (!name.isNullOrBlank()) {
                            it[Users.nickname] = name
                        }
                        if (avatar != null) {
                            it[Users.icon] = avatar
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

    override suspend fun checkUserExists(id: Long): Result<Boolean> {
        return exposedUserDatabase.dbSearch {
            search {
                User.Companion.find {
                    Users.id eq id
                }
            }
            isNotEmpty()
        }
    }

    override suspend fun getUserAuthDataByAid(
        predicate: SqlExpressionBuilder.() -> Op<Boolean>
    ): Result<Pair<String, Long>?> {
        return exposedUserDatabase.dbSearch {
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

    override suspend fun getUserAuthDataBy(
        predicate: SqlExpressionBuilder.() -> Op<Boolean>
    ): Result<Pair<String, Long>?> {
        return exposedUserDatabase.dbSearch {
            search {
                Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
            }
            first {
                it[Users.publicKey] to it[Users.id]
            }
        }
    }

    override suspend fun getUserRawResultList(objectListFetch: ObjectListFetch): Result<List<UserRawResult>> {
        return exposedUserDatabase.dbSearch {
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
        return exposedUserDatabase.dbSearch {
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

    override suspend fun getUserAlternatUserRawResultList(uid: PrimaryKey): Result<List<UserRawResult>> {
        return exposedUserDatabase.dbSearch {
            search {
                Users.join(AlternateAccounts, JoinType.INNER, Users.id, AlternateAccounts.hostId) {
                    AlternateAccounts.hostId eq uid
                }.select(Users.fields)
            }
            map(::mapUserInfo)
        }
    }

    override suspend fun addReadLog(userTopicRead: UserTopicRead): Result<Unit> {
        return exposedUserDatabase.dbQuery {
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

    override suspend fun getTopicReadList(
        parentIds: List<PrimaryKey>,
        uid: PrimaryKey
    ): Result<List<UserTopicRead>> {
        return exposedUserDatabase.dbSearch {
            search {
                UserTopicReads.selectAll().where {
                    UserTopicReads.uid eq uid and (UserTopicReads.objectId inList parentIds)
                }
            }
            map(UserTopicRead::wrapRow)
        }
    }

    override suspend fun insertUserLog(log: UserLog): Result<Unit> {
        return exposedUserDatabase.dbQuery {
            check(UserLogs.insert {
                it[UserLogs.id] = log.id
                it[UserLogs.uid] = log.uid
                it[UserLogs.type] = log.type
                it[UserLogs.objectId] = log.objectId
                it[UserLogs.objectType] = log.objectType
                it[UserLogs.createdTime] = log.createdTime
            }.insertedCount > 0) {
                "Insert user log failed"
            }
        }
    }

    override suspend fun addDevice(uid: PrimaryKey, endpointUrl: String): Result<Unit> {
        return exposedUserDatabase.dbQuery {
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
        endpointUrl: String
    ): Result<Int> {
        return exposedUserDatabase.dbQuery {
            UserDevices.deleteWhere {
                (UserDevices.uid eq uid) and (UserDevices.endpointUrl eq endpointUrl)
            }
        }
    }

    override suspend fun getUserDevices(uid: List<PrimaryKey>): Result<List<UserDevice>> {
        return exposedUserDatabase.dbSearch {
            search {
                UserDevices.selectAll().where {
                    UserDevices.uid inList uid
                }
            }
            map(UserDevice::wrapRow)
        }
    }

    override suspend fun isMemberJoined(
        objectId: PrimaryKey,
        uid: PrimaryKey?
    ): Result<Boolean> {
        return if (uid == null) {
            Result.success(false)
        } else {
            exposedUserDatabase.dbSearch {
                search {
                    MemberJoins.selectAll().where {
                        (MemberJoins.objectId eq objectId) and (MemberJoins.uid eq uid)
                    }
                }
                isNotEmpty()
            }
        }
    }

    override suspend fun addRoomJoin(
        room: PrimaryKey,
        id: PrimaryKey,
        time: LocalDateTime,
    ): Result<Unit> {
        return exposedUserDatabase.dbQuery {
            MemberJoin.addRoomJoinRaw(room, id, time)
        }
    }

    override suspend fun exit(
        containerId: PrimaryKey,
        id: PrimaryKey
    ): Result<Int> {
        return exposedUserDatabase.dbQuery {
            MemberJoins.deleteWhere {
                objectId eq containerId and (uid eq id)
            }
        }
    }

    override suspend fun addCommunityJoin(
        id: PrimaryKey,
        community: PrimaryKey,
        time: LocalDateTime,
    ): Result<Unit> {
        return exposedUserDatabase.dbQuery {
            MemberJoin.addCommunityJoinRaw(id, community, time)
        }
    }

    override suspend fun getJoinedUserList(roomId: PrimaryKey): Result<List<MemberJoin>> {
        return exposedUserDatabase.dbSearch {
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
        uid: PrimaryKey
    ): Result<List<MemberJoin>> {
        return exposedUserDatabase.dbSearch {
            search {
                MemberJoins.select(MemberJoins.fields).where {
                    (MemberJoins.uid eq uid) and (MemberJoins.objectId inList parentIds)
                }
            }
            map(MemberJoin::wrapRow)
        }
    }

    override suspend fun getMemberCount(parentIds: List<PrimaryKey>): Result<List<Pair<Long, Long>>> {
        return exposedUserDatabase.dbSearch {
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
        uid: PrimaryKey?
    ): Result<Triple<Map<PrimaryKey, MemberJoin>, Map<PrimaryKey, UserTopicRead>, Map<Long, Long>>> {
        return merge({
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
    }

    override suspend fun addAcgForUser(
        acgList: List<Pair<PrimaryKey, Int>>,
        userAcgMap: Map<Long, Long>,
        list: List<Topic>,
        taskRecordId: PrimaryKey
    ): Result<Unit> {
        return exposedUserDatabase.dbQuery {
            acgList.forEach { (id, acg) ->
                userAcgMap[id]?.let { oldAcgAmount ->
                    Users.update({
                        Users.id eq id
                    }) {
                        it[Users.acgAmount] = oldAcgAmount + acg
                    }
                    AssetTransaction.addAssetTransaction(
                        AssetTransaction(
                            AssetType.ACG,
                            oldAcgAmount,
                            oldAcgAmount + acg
                        )
                    )
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

    override suspend fun getMediaPaginationList(
        uid: PrimaryKey,
        primaryKeyFetch: PrimaryKeyFetch
    ): Result<Pair<List<Media>, Long>> {
        return exposedUserDatabase.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq uid
                }.bindPaginationQuery(Medias, primaryKeyFetch)
            }
            map(Media::wrapRow)
        }.mapResult { list ->
            exposedUserDatabase.dbSearch {
                search {
                    Medias.selectAll().where {
                        Medias.owner eq uid
                    }
                }
                count()
            }.map { count ->
                list to count
            }
        }
    }

    override suspend fun getMedia(owner: PrimaryKey, name: String): Result<Media?> {
        return exposedUserDatabase.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq owner and (Medias.name eq name)
                }
            }
            first(Media::wrapRow)
        }
    }

    override suspend fun getMediaByIds(ids: List<PrimaryKey>): Result<List<Media>> {
        return exposedUserDatabase.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.id inList ids
                }
            }
            map(Media::wrapRow)
        }
    }

    override suspend fun getMediaListByOwner(owner: PrimaryKey): Result<List<Media>> {
        return exposedUserDatabase.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.owner eq owner
                }.orderBy(Medias.id, SortOrder.DESC)
            }
            map(Media::wrapRow)
        }
    }

    override suspend fun getMediaByNames(names: List<String?>): Result<List<Media>> {
        if (names.filterNotNull().isEmpty()) {
            return Result.success(emptyList())
        }
        return exposedUserDatabase.dbSearch {
            search {
                Medias.selectAll().where {
                    Medias.fullName inList names.filterNotNull()
                }.orderBy(Medias.id, SortOrder.DESC)
            }
            map(Media::wrapRow)
        }
    }

    override suspend fun insertMediaRefs(
        objectId: PrimaryKey,
        objectType: ObjectType,
        mediaName: List<Pair<PrimaryKey, String>>
    ): Result<List<ResultRow>> {
        return exposedUserDatabase.dbQuery {
            MediaRefs.batchInsert(mediaName) {
                this[MediaRefs.objectId] = objectId
                this[MediaRefs.objectType] = objectType
                this[MediaRefs.mediaName] = it.second
                this[MediaRefs.author] = it.first
            }
        }
    }

    override suspend fun getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?> {
        return exposedUserDatabase.dbSearch {
            search {
                TaskRecords.selectAll().where {
                    TaskRecords.type eq type
                }.orderBy(TaskRecords.id, SortOrder.DESC)
            }
            first(TaskRecord::wrapRow)
        }
    }
}
