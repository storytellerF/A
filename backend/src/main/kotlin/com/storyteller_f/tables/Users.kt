package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.types.PaginationResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Users : BaseTable() {
    val aid = varchar("aid", USER_ID_LENGTH).uniqueIndex().nullable()
    val publicKey = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
    val address = varchar("pub_address", ADDRESS_LENGTH).uniqueIndex()
    val icon = varchar("icon", ICON_LENGTH).nullable()
    val nickname = varchar("nickname", USER_NICKNAME).index()
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
                row[Users.aid],
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

fun findUserByAId(aid: String): ResultRow? {
    return Users.selectAll().where {
        Users.aid eq aid
    }.limit(1).firstOrNull()
}

fun createUser(
    user: User
): User {
    Users.insert {
        it[id] = user.id
        it[publicKey] = user.publicKey
        it[address] = user.address
        it[nickname] = user.nickname
        it[createdTime] = user.createdTime
    }
    return user
}


suspend fun getUserAid(id: PrimaryKey): Result<String?> = DatabaseFactory.first({
    aid
}, User::wrapRow) {
    User.findById(id)
}

fun User.toUserInfo(): UserInfo {
    return UserInfo(id, address, 0, aid, nickname, null)
}

fun toFinalUserInfo(p: Pair<UserInfo, String?>, backend: Backend): Result<UserInfo> {
    val (userInfo, icon) = p
    return backend.mediaService.get("apic", listOf(icon)).map { value ->
        userInfo.copy(avatar = value.firstOrNull()?.let {
            MediaInfo(it)
        })
    }
}

suspend fun getUser(
    it: PrimaryKey,
    backend: Backend
) = getUserById1(it).mapResultNotNull {
    toFinalUserInfo(it, backend)
}


suspend fun getUserByAid(
    aid: String,
    backend: Backend
) = getUserByAid1(aid).mapResultNotNull {
    toFinalUserInfo(it, backend)
}

suspend fun getUserById1(it: PrimaryKey): Result<Pair<UserInfo, String?>?> = DatabaseFactory.first({
    toUserInfo() to icon
}, User::wrapRow) {
    User.findById(it)
}

suspend fun getUserByAid1(aid: String): Result<Pair<UserInfo, String?>?> = DatabaseFactory.first({
    toUserInfo() to icon
}, User::wrapRow) {
    User.find {
        Users.aid eq aid
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
        val join = Users.join(MemberJoins, JoinType.INNER, Users.id, MemberJoins.uid)
        if (getCount) {
            join.selectAll()
        } else {
            join.select(Users.fields)
        }.where {
            MemberJoins.objectId eq objectId
        }
    } else {
        Users.selectAll()
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
        backend.mediaService.get("apic", pairs.map {
            it.second
        }).map { value ->
            PaginationResult(pairs.mapIndexed { index, pair ->
                pair.first.copy(avatar = value[index]?.let {
                    MediaInfo(it)
                })
            }, count)
        }
    }
}