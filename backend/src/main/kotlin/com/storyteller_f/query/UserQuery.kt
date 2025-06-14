package com.storyteller_f.query

import com.storyteller_f.*
import com.storyteller_f.ObjectFetch
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.AlgoType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PassType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import com.storyteller_f.tables.Aids
import com.storyteller_f.tables.AlternateAccounts.hostId
import com.storyteller_f.tables.Users
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
import org.jetbrains.exposed.sql.*

suspend fun ExposedDatabaseSession.getUserAid(id: PrimaryKey): Result<String?> =
    dbSearch {
        search {
            Aids.selectAll().where {
                Aids.objectId eq id
            }
        }
        first {
            it[Aids.value]
        }
    }

suspend fun ExposedDatabaseSession.getUserRawResult(
    objectFetch: ObjectFetch
): Result<UserRawResult?> = dbSearch {
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

suspend fun ExposedDatabaseSession.getMemberPaginationResult(
    objectId: PrimaryKey?,
    word: String?,
    primaryKeyFetch: PrimaryKeyFetch
): Result<PaginationResult<UserRawResult>> {
    return dbSearch {
        search {
            buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
                Users,
                primaryKeyFetch
            )
        }
        map(::mapUserInfo)
    }.mapResult { pairs ->
        dbSearch {
            search {
                buildSearchMembersQuery(objectId, true, word)
            }
            count()
        }.map { value ->
            PaginationResult(pairs, value)
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

suspend fun ExposedDatabaseSession.getUserRawResultAndPublicKeyByAddress(
    ad: String
) = dbSearch {
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
        Pair(UserRawResult(value, value.icon), value.publicKey)
    }
}

suspend fun ExposedDatabaseSession.createUser(
    ad: String,
    name: String,
    newId: PrimaryKey,
    pk: String
) = dbQuery {
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
    user
}

suspend fun ExposedDatabaseSession.isUserNotExists(pk: String): Result<Boolean> {
    return dbSearch {
        search {
            User.find {
                Users.publicKey eq pk
            }
        }
        isEmpty()
    }
}

suspend fun ExposedDatabaseSession.updateUserInfo(
    id: PrimaryKey,
    newUser: UpdateUserBody
) = dbQuery {
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

suspend fun ExposedDatabaseSession.checkUserExists(id: Long): Result<Boolean> {
    return dbSearch {
        search {
            User.find {
                Users.id eq id
            }
        }
        isNotEmpty()
    }
}

suspend fun ExposedDatabaseSession.getUserAuthDataByAid(
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
) =
    dbSearch {
        search {
            Users.join(Aids, JoinType.LEFT, Users.id, Aids.objectId)
                .select(listOf(Users.publicKey, Users.id))
                .where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

suspend fun ExposedDatabaseSession.getUserAuthDataBy(
    predicate: SqlExpressionBuilder.() -> Op<Boolean>
) =
    dbSearch {
        search {
            Users.select(listOf(Users.publicKey, Users.id)).where(predicate)
        }
        first {
            it[Users.publicKey] to it[Users.id]
        }
    }

suspend fun ExposedDatabaseSession.getUserRawResultList(objectListFetch: ObjectListFetch): Result<List<UserRawResult>> =
    dbSearch {
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

suspend fun ExposedDatabaseSession.getUserAcgByIds(objectListFetch: ObjectListFetch) =
    dbSearch {
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

suspend fun ExposedDatabaseSession.getUserAlternatUserRawResultList(uid: PrimaryKey): Result<List<UserRawResult>> {
    return dbSearch {
        search {
            Users.join(AlternateAccounts, JoinType.INNER, Users.id, hostId) {
                hostId eq uid
            }.select(Users.fields)
        }
        map(::mapUserInfo)
    }
}
