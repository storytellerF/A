package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.type.OKey
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
    val aid: String?, val publicKey: String, val address: String, val icon: String?, val nickname: String,
    id: OKey, createdTime: LocalDateTime
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

        fun findById(it: OKey): User? {
            return find {
                Users.id eq it
            }.limit(1).firstOrNull()?.let(::wrapRow)
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
