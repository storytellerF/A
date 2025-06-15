package com.storyteller_f.a.exposed

import com.impossibl.postgres.jdbc.PGSQLIntegrityConstraintViolationException
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.net.ConnectException
import java.sql.SQLIntegrityConstraintViolationException

abstract class BaseTable : Table() {
    val id = customPrimaryKey("id")
    val createdTime = datetime("created_time")

    override val primaryKey = PrimaryKey(id)
}

abstract class BaseEntity(val id: PrimaryKey, val createdTime: LocalDateTime)

const val PUBLIC_KEY_LENGTH = 512
const val ADDRESS_LENGTH = 100
const val USER_NICKNAME = 20
const val COMMUNITY_NAME_LENGTH = 10
const val AID_LENGTH = 20
const val ROOM_NAME_LENGTH = 10

// 最长60，如果超过60 会进行裁切然后在后面添加uuid，保存时预留一部分空间
const val MEDIA_NAME_LENGTH = 120

fun Throwable.isDup(): Boolean {
    if (this is SQLIntegrityConstraintViolationException) return true
    if (this is ExposedSQLException) {
        val throwable = this.cause
        Napier.e(throwable = this) {
            if (throwable is PGSQLIntegrityConstraintViolationException) {
                "dup check exception ${throwable.sqlState} ${throwable.errorCode}"
            } else {
                "dup check exception"
            }
        }
        return (throwable is PGSQLIntegrityConstraintViolationException && throwable.sqlState == "unique")
    }
    return false
}

fun isConnectFailed(e: Throwable): Boolean = e is ConnectException

fun Table.customPrimaryKey(name: String) = long(name)

fun Table.objectType(name: String) = enumerationByName<ObjectType>(name, 10)

fun Table.userPublicKey() = varchar("public_key", PUBLIC_KEY_LENGTH).uniqueIndex()
fun Table.userAddress() = varchar("pub_address", ADDRESS_LENGTH).uniqueIndex()
fun Table.userName() = varchar("nickname", USER_NICKNAME).index()

fun Table.roomName() = varchar("name", ROOM_NAME_LENGTH).index()

fun <T : Table> T.emoji() = varchar("emoji", 20)

fun Table.communityName() = varchar("name", COMMUNITY_NAME_LENGTH).index()

fun Table.titleName() = varchar("name", 20)
