package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.types.PaginationResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Users : BaseTable() {
    val publicKey = userPublicKey()
    val address = userAddress()
    val icon = userIcon()
    val nickname = userName()
}

class User(
    val aid: String?,
    val publicKey: String,
    val address: String,
    val icon: String?,
    val nickname: String,
    id: PrimaryKey,
    createdTime: LocalDateTime
) :
    BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): User {
            return User(
                row[Aids.value],
                row[Users.publicKey],
                row[Users.address],
                row[Users.icon],
                row[Users.nickname],
                row[Users.id],
                row[Users.createdTime]
            )
        }

        fun findById(it: PrimaryKey) = find {
            Users.id eq it
        }

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<ResultRow> {
            return Users.selectAll().where(function)
        }
    }
}

fun findUserByAId(aid: String): Query {
    return Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
        Aids.value eq aid
    }.limit(1)
}

suspend fun getUserAid(id: PrimaryKey): Result<String?> = DatabaseFactory.first({
    it[Aids.value]
}) {
    Aids.selectAll().where {
        Aids.objectId eq id
    }
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

fun toFinalUserInfo(p: Pair<UserInfo, String?>, backend: Backend): Result<UserInfo> {
    val (userInfo, icon) = p
    return backend.mediaService.get("amedia", listOf(icon)).map { value ->
        userInfo.copy(avatar = value.firstOrNull())
    }
}

suspend fun getUserByAid(
    aid: String,
    backend: Backend
) = getRawUserByAid(aid).mapResultNotNull {
    toFinalUserInfo(it, backend)
}

suspend fun getUser(
    it: PrimaryKey,
    backend: Backend
) = getRawUserById(it).mapResultNotNull {
    toFinalUserInfo(it, backend)
}

suspend fun getRawUserByAid(aid: String) = DatabaseFactory.first({
    toUserInfo() to icon
}, User::wrapRow) {
    findUserByAId(aid)
}

suspend fun getRawUserById(it: PrimaryKey): Result<Pair<UserInfo, String?>?> = DatabaseFactory.first({
    toUserInfo() to icon
}, User::wrapRow) {
    Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
        Users.id eq it
    }
}

suspend fun commonPaginationMemberList(
    objectId: PrimaryKey?,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    word: String?
): Result<Pair<List<Pair<UserInfo, String?>>, Long>> {
    return DatabaseFactory.mapQuery({
        first.toUserInfo() to second
    }, {
        User.wrapRow(it) to it[Users.icon]
    }) {
        buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
            Users,
            prePageToken,
            nextPageToken,
            size
        )
    }.mapResult { pairs ->
        DatabaseFactory.count {
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
            join.select(Users.fields + MemberJoins.joinTime + Aids.value)
        }
    } else {
        Users.join(Aids, JoinType.LEFT, Rooms.id, Aids.objectId).select(Users.fields + Aids.value)
    }

    if (!(word.isNullOrBlank())) {
        query.andWhere {
            Users.nickname like "%$word%"
        }
    }
    return query
}

suspend fun searchMembers(
    objectId: PrimaryKey?,
    backend: Backend,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    word: String?
): Result<PaginationResult<UserInfo>> {
    return commonPaginationMemberList(objectId, prePageToken, nextPageToken, size, word).mapResult { (pairs, count) ->
        backend.mediaService.get("amedia", pairs.map {
            it.second
        }).map { value ->
            PaginationResult(pairs.mapIndexed { index, pair ->
                pair.first.copy(avatar = value[index])
            }, count)
        }
    }
}

suspend fun getUserByAddress(ad: String): Result<Triple<UserInfo, String?, String>?> = DatabaseFactory.first({
    Triple(toUserInfo(), icon, publicKey)
}, User::wrapRow) {
    Users
        .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
        .select(Users.fields + Aids.value)
        .where {
            Users.address eq ad
        }
}

suspend fun createUser(
    ad: String,
    name: String,
    newId: PrimaryKey,
    pk: String
): Result<Pair<UserInfo, Nothing?>> = DatabaseFactory.query({
    toUserInfo() to null
}) {
    val user = User(null, pk, ad, null, name, newId, now())
    Users.insert {
        it[id] = user.id
        it[publicKey] = user.publicKey
        it[address] = user.address
        it[nickname] = user.nickname
        it[createdTime] = user.createdTime
    }
    user
}

suspend fun isUserNotExists(pk: String): Result<Boolean> = DatabaseFactory.isEmpty {
    User.find {
        Users.publicKey eq pk
    }
}

suspend fun updateUser(
    id: PrimaryKey,
    newUser: UserInfo
): Result<Boolean> {
    val aid = newUser.aid
    return DatabaseFactory.dbQuery {
        listOf({
            if (newUser.nickname.isNotBlank()) {
                Users.update({
                    Users.id eq id
                }) {
                    it[nickname] = newUser.nickname
                } > 0
            } else {
                true
            }
        }, {
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

suspend fun checkUserExists(id: Long) = DatabaseFactory.first({
    id
}, User::wrapRow) {
    User.findById(id)
}

suspend fun getUserAuthDataByAid(predicate: SqlExpressionBuilder.() -> Op<Boolean>) =
    DatabaseFactory.first({
        it[Users.publicKey] to it[Users.id]
    }) {
        Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
            .select(listOf(Users.publicKey, Users.id))
            .where(predicate)
    }

suspend fun getUserAuthDataBy(predicate: SqlExpressionBuilder.() -> Op<Boolean>) =
    DatabaseFactory.first({
        it[Users.publicKey] to it[Users.id]
    }) {
        Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
    }
