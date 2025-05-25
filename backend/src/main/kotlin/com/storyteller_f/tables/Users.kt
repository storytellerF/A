package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.AlgoType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PassType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Users : BaseTable() {
    val publicKey = userPublicKey()
    val address = userAddress()
    val icon = userIcon()
    val nickname = userName()
    val acgAmount = long("acg_amount").default(0)
    val passType = enumerationByName<PassType>("pass_type", 20).default(PassType.RAW)
    val algoType = enumerationByName<AlgoType>("algo_type", 20).default(AlgoType.P256)
}

class User(
    val aid: String?,
    val publicKey: String,
    val address: String,
    val icon: String?,
    val nickname: String,
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val acgAmount: Long,
    val passType: PassType,
    val algoType: AlgoType,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): User {
            return with(Users) {
                User(
                    row[Aids.value],
                    row[publicKey],
                    row[address],
                    row[icon],
                    row[nickname],
                    row[id],
                    row[createdTime],
                    row[acgAmount],
                    row[passType],
                    row[algoType]
                )
            }
        }

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<ResultRow> {
            return Users.selectAll().where(function)
        }
    }
}

fun mapUserInfo(it: ResultRow): Pair<UserInfo, String?> {
    return User.wrapRow(it).toUserInfo() to it[Users.icon]
}

suspend fun DatabaseFactory.getUserAid(backend: Backend, id: PrimaryKey): Result<String?> =
    dbSearch(backend) {
        search {
            Aids.selectAll().where {
                Aids.objectId eq id
            }
        }
        first {
            it[Aids.value]
        }
    }

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

sealed interface ObjectFetch {
    data class AidFetch(val aid: String) : ObjectFetch
    data class IdFetch(val id: PrimaryKey) : ObjectFetch
}

suspend fun DatabaseFactory.getUserAndRelatedMedia(
    backend: Backend,
    fetch: ObjectFetch
): Result<UserInfo?> {
    return dbSearch(backend) {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
                when (fetch) {
                    is ObjectFetch.AidFetch -> Aids.value eq fetch.aid
                    is ObjectFetch.IdFetch -> Users.id eq fetch.id
                }
            }
        }
        first(::mapUserInfo)
    }.mapResultIfNotNull {
        processUserList(backend, listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun DatabaseFactory.getRawUser(
    backend: Backend,
    it: PrimaryKey
): Result<Pair<UserInfo, String?>?> = dbSearch(backend) {
    search {
        Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
            Users.id eq it
        }
    }
    first(::mapUserInfo)
}

suspend fun DatabaseFactory.commonPaginationMemberList(
    backend: Backend,
    objectId: PrimaryKey?,
    word: String?,
    pagingFetch: PagingFetch
): Result<Pair<List<Pair<UserInfo, String?>>, Long>> {
    return dbSearch(backend) {
        search {
            buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                Users,
                pagingFetch
            )
        }
        map(::mapUserInfo)
    }.mapResult { pairs ->
        count(backend) {
            buildSearchMembersQuery(objectId, true, word)
        }.map { value ->
            pairs to value
        }
    }
}

private fun buildSearchMembersQuery(objectId: PrimaryKey?, getCount: Boolean, word: String?): Query {
    val query = if (objectId != null) {
        val join = Users
            .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
            .join(MemberJoins, JoinType.INNER, Users.id, MemberJoins.uid) {
                MemberJoins.objectId eq objectId
            }
        if (getCount) {
            join.selectAll()
        } else {
            join.select(Users.fields + MemberJoins.joinedTime + Aids.value)
        }
    } else {
        Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).select(Users.fields + Aids.value)
    }

    if (!(word.isNullOrBlank())) {
        query.andWhere {
            Users.nickname like "%$word%"
        }
    }
    return query
}

suspend fun DatabaseFactory.searchMembers(
    backend: Backend,
    objectId: PrimaryKey?,
    word: String?,
    pagingFetch: PagingFetch
): Result<PaginationResult<UserInfo>?> {
    return commonPaginationMemberList(backend, objectId, word, pagingFetch).mapResult { (pairs, count) ->
        processUserList(backend, pairs).mapIfNotNull {
            PaginationResult(it, count)
        }
    }
}

suspend fun DatabaseFactory.getUserByAddress(
    backend: Backend,
    ad: String
): Result<Triple<UserInfo, String?, String>?> = dbSearch(backend) {
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
        Triple(value.toUserInfo(), value.icon, value.publicKey)
    }
}

suspend fun DatabaseFactory.createUser(
    backend: Backend,
    ad: String,
    name: String,
    newId: PrimaryKey,
    pk: String
): Result<UserInfo> {
    return dbQuery(backend) {
        val user = User(null, pk, ad, null, name, newId, now(), 0, PassType.RAW, AlgoType.P256)
        check(Users.insert {
            it[id] = user.id
            it[publicKey] = user.publicKey
            it[address] = user.address
            it[nickname] = user.nickname
            it[createdTime] = user.createdTime
        }.insertedCount > 0) {
            "insert user failed"
        }
        user.toUserInfo()
    }
}

suspend fun DatabaseFactory.isUserNotExists(backend: Backend, pk: String): Result<Boolean> =
    isEmpty(
        backend
    ) {
        User.find {
            Users.publicKey eq pk
        }
    }

suspend fun DatabaseFactory.updateUser(
    backend: Backend,
    id: PrimaryKey,
    newUser: UpdateUserBody
) = dbQuery(backend) {
    listOf({
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
                    it[icon] = avatar.ifEmpty { null }
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

suspend fun DatabaseFactory.checkUserExists(backend: Backend, id: Long) =
    isNotEmpty(backend) {
        User.find {
            Users.id eq id
        }
    }

suspend fun DatabaseFactory.getUserAuthDataByAid(
    backend: Backend,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
) =
    dbSearch(backend) {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .select(listOf(Users.publicKey, Users.id))
                .where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

suspend fun DatabaseFactory.getUserAuthDataBy(
    backend: Backend,
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
) =
    dbSearch(backend) {
        search {
            Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

suspend fun DatabaseFactory.getUsersByIds(
    backend: Backend,
    ids: List<PrimaryKey>
) = dbSearch(backend) {
    search {
        Users
            .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
            .select(Users.fields + Aids.value)
            .where {
                Users.id inList ids
            }
    }
    map(::mapUserInfo)
}.mapResult {
    processUserList(backend, it)
}

suspend fun DatabaseFactory.getRawUsersByAids(backend: Backend, ids: List<String>) =
    dbSearch(backend) {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .select(Users.fields + Aids.value)
                .where {
                    Aids.value inList ids
                }
        }
        map(::mapUserInfo)
    }

suspend fun DatabaseFactory.getUserAcgByIds(backend: Backend, ids: List<PrimaryKey>) =
    dbSearch(backend) {
        search {
            Users.select(Users.fields)
                .where {
                    Users.id inList ids
                }
        }
        map {
            it[Users.id] to it[Users.acgAmount]
        }
    }

suspend fun processUserList(
    backend: Backend,
    pairs: List<Pair<UserInfo, String?>>
) = DatabaseFactory.getMediaInfoList(backend, pairs.map {
    it.second
}).mapIfNotNull { value ->
    pairs.mapIndexed { index, pair ->
        pair.first.copy(avatar = value[index])
    }
}
