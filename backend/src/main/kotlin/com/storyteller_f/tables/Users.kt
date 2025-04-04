package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.UpdateUserBody
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

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<ResultRow> {
            return Users.selectAll().where(function)
        }
    }
}

suspend fun DatabaseFactory.getUserAid(id: PrimaryKey): Result<String?> = first({
    it[Aids.value]
}) {
    Aids.selectAll().where {
        Aids.objectId eq id
    }
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

suspend fun DatabaseFactory.getUserByAid(
    aid: String,
    backend: Backend
) = getRawUserByAid(aid).mapResultNotNull {
    processUserList(backend, listOf(it)).map(List<UserInfo>::first)
}

suspend fun DatabaseFactory.getUser(
    it: PrimaryKey,
    backend: Backend
) = getRawUserById(it).mapResultNotNull {
    processUserList(backend, listOf(it)).map(List<UserInfo>::first)
}

suspend fun DatabaseFactory.getRawUserByAid(aid: String) = first({
    toUserInfo() to icon
}, User::wrapRow) {
    Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
        Aids.value eq aid
    }.limit(1)
}

suspend fun DatabaseFactory.getRawUserById(it: PrimaryKey): Result<Pair<UserInfo, String?>?> = first({
    toUserInfo() to icon
}, User::wrapRow) {
    Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId).selectAll().where {
        Users.id eq it
    }.limit(1)
}

suspend fun DatabaseFactory.commonPaginationMemberList(
    objectId: PrimaryKey?,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    word: String?
): Result<Pair<List<Pair<UserInfo, String?>>, Long>> {
    return mapQuery({
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
        count {
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
    objectId: PrimaryKey?,
    backend: Backend,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    word: String?
): Result<PaginationResult<UserInfo>> {
    return commonPaginationMemberList(objectId, prePageToken, nextPageToken, size, word).mapResult { (pairs, count) ->
        processUserList(backend, pairs).map {
            PaginationResult(it, count)
        }
    }
}

suspend fun DatabaseFactory.getUserByAddress(ad: String): Result<Triple<UserInfo, String?, String>?> = first({
    Triple(toUserInfo(), icon, publicKey)
}, User::wrapRow) {
    Users
        .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
        .select(Users.fields + Aids.value)
        .where {
            Users.address eq ad
        }
}

suspend fun DatabaseFactory.createUser(
    ad: String,
    name: String,
    newId: PrimaryKey,
    pk: String
): Result<Pair<UserInfo, Nothing?>> {
    return query({
        this to null
    }) {
        val user = User(null, pk, ad, null, name, newId, now())
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

suspend fun DatabaseFactory.isUserNotExists(pk: String): Result<Boolean> = isEmpty {
    User.find {
        Users.publicKey eq pk
    }
}

suspend fun DatabaseFactory.updateUser(
    id: PrimaryKey,
    newUser: UpdateUserBody
) = dbQuery {
    listOf({
        val avatar = newUser.avatar
        val name = newUser.nickname
        if (!name.isNullOrBlank() || !avatar.isNullOrBlank()) {
            Users.update({
                Users.id eq id
            }) {
                if (!name.isNullOrBlank()) {
                    it[nickname] = name
                }
                if (!avatar.isNullOrBlank()) {
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

suspend fun DatabaseFactory.checkUserExists(id: Long) = first({
    id
}, User::wrapRow) {
    User.find {
        Users.id eq id
    }
}

suspend fun DatabaseFactory.getUserAuthDataByAid(predicate: SqlExpressionBuilder.() -> Op<Boolean>) =
    first({
        it[Users.publicKey] to it[Users.id]
    }) {
        Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
            .select(listOf(Users.publicKey, Users.id))
            .where(predicate)
    }

suspend fun DatabaseFactory.getUserAuthDataBy(predicate: SqlExpressionBuilder.() -> Op<Boolean>) =
    first({
        it[Users.publicKey] to it[Users.id]
    }) {
        Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
    }

suspend fun DatabaseFactory.getUsersByIds(ids: List<PrimaryKey>, backend: Backend) = mapQuery({
    toUserInfo() to icon
}, User::wrapRow) {
    Users
        .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
        .select(Users.fields + Aids.value)
        .where {
            Users.id inList ids
        }
}.mapResult {
    processUserList(backend, it)
}

suspend fun DatabaseFactory.getUsersByAids(ids: List<String>) = mapQuery({
    toUserInfo() to icon
}, User::wrapRow) {
    Users
        .join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
        .select(Users.fields + Aids.value)
        .where {
            Aids.value inList ids
        }
}

suspend fun processUserList(
    backend: Backend,
    pairs: List<Pair<UserInfo, String?>>
): Result<List<UserInfo>> = backend.mediaService.get(AMEDIA_DEFAULT_BUCKET, pairs.map {
    it.second
}).map { value ->
    pairs.mapIndexed { index, pair ->
        pair.first.copy(avatar = value[index])
    }
}
