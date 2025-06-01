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
import com.storyteller_f.types.PrimaryKeyFetch
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

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>): Query {
            return Users.selectAll().where(function)
        }
    }
}

fun mapUserInfo(it: ResultRow): UserRawResult {
    return UserRawResult(User.wrapRow(it).toUserInfo(), it[Users.icon])
}

suspend fun Backend.getUserAid(id: PrimaryKey): Result<String?> =
    databaseSession.dbSearch {
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

sealed interface ObjectListFetch {
    data class AidListFetch(val aidList: List<String>) : ObjectListFetch
    data class IdListFetch(val idList: List<PrimaryKey>) : ObjectListFetch
}

data class UserRawResult(val user: UserInfo, val avatar: String?)

fun Query.bindUserFetchQuery(fetch: ObjectFetch): Query {
    return where {
        when (fetch) {
            is ObjectFetch.AidFetch -> Aids.value eq fetch.aid
            is ObjectFetch.IdFetch -> Users.id eq fetch.id
        }
    }
}

suspend fun Backend.getUserInfoAndRelatedMedia(
    fetch: ObjectFetch
): Result<UserInfo?> {
    return getUserRawResult(fetch).mapResultIfNotNull {
        processUserRawResultToUserInfo(listOf(it)).mapIfNotNull(List<UserInfo>::first)
    }
}

suspend fun Backend.getUserRawResult(
    objectFetch: ObjectFetch
): Result<UserRawResult?> = databaseSession.dbSearch {
    search {
        Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().bindUserFetchQuery(objectFetch)
    }
    first(::mapUserInfo)
}

suspend fun Backend.commonPaginationMemberList(
    objectId: PrimaryKey?,
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<Pair<List<UserRawResult>, Long>> {
    return databaseSession.dbSearch {
        search {
            buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                Users,
                primaryKeyFetch
            )
        }
        map(::mapUserInfo)
    }.mapResult { pairs ->
        databaseSession.dbSearch {
            search {
                buildSearchMembersQuery(objectId, true, word)
            }
            count()
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

suspend fun Backend.searchMembers(
    objectId: PrimaryKey?,
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<UserInfo>?> {
    return commonPaginationMemberList(objectId, word, primaryKeyFetch).mapResult { (pairs, count) ->
        processUserRawResultToUserInfo(pairs).mapIfNotNull {
            PaginationResult(it, count)
        }
    }
}

suspend fun Backend.getUserRawResultAndPublicKeyByAddress(
    ad: String
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
        Pair(UserRawResult(value.toUserInfo(), value.icon), value.publicKey)
    }
}

suspend fun Backend.createUser(
    ad: String,
    name: String,
    newId: PrimaryKey,
    pk: String
): Result<UserInfo> {
    return databaseSession.dbQuery {
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

suspend fun Backend.isUserNotExists(pk: String): Result<Boolean> {
    return databaseSession.dbSearch {
        search {
            User.find {
                Users.publicKey eq pk
            }
        }
        isEmpty()
    }
}

suspend fun Backend.updateUserInfo(
    id: PrimaryKey,
    newUser: UpdateUserBody
) = databaseSession.dbQuery {
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

suspend fun Backend.checkUserExists(id: Long): Result<Boolean> {
    return databaseSession.dbSearch {
        search {
            User.find {
                Users.id eq id
            }
        }
        isNotEmpty()
    }
}

suspend fun Backend.getUserAuthDataByAid(
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
) =
    databaseSession.dbSearch {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .select(listOf(Users.publicKey, Users.id))
                .where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

suspend fun Backend.getUserAuthDataBy(
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
) =
    databaseSession.dbSearch {
        search {
            Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

suspend fun Backend.getUsersInfoByIds(
    ids: List<PrimaryKey>
) = databaseSession.dbSearch {
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
    processUserRawResultToUserInfo(it)
}

suspend fun Backend.getUserRawResultByAids(ids: List<String>) =
    databaseSession.dbSearch {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .select(Users.fields + Aids.value)
                .where {
                    Aids.value inList ids
                }
        }
        map(::mapUserInfo)
    }

suspend fun Backend.getUserAcgByIds(ids: List<PrimaryKey>) =
    databaseSession.dbSearch {
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

suspend fun Backend.processUserRawResultToUserInfo(
    pairs: List<UserRawResult>
) = getMediaInfoList(pairs.map {
    it.avatar
}).mapIfNotNull { value ->
    pairs.mapIndexed { index, pair ->
        pair.user.copy(avatar = value[index])
    }
}
