package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert

class UserLogTypeColumnType : ColumnType<UserLogType>() {
    override fun sqlType(): String {
        return "varchar(10)"
    }

    override fun valueFromDB(value: Any): UserLogType {
        if (value is UserLogType) return value
        return UserLogType.valueOf(value as String)
    }

    override fun notNullValueToDB(value: UserLogType): Any {
        return value.name
    }
}

fun Table.userLogType(name: String) = registerColumn(name, UserLogTypeColumnType())

object UserLogs : BaseTable() {
    val uid = customPrimaryKey("uid")
    val type = userLogType("type")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
}

class UserLog(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val uid: PrimaryKey,
    val type: UserLogType,
    val objectId: PrimaryKey,
    val objectType: ObjectType
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): UserLog {
            return UserLog(
                resultRow[UserLogs.id],
                resultRow[UserLogs.createdTime],
                resultRow[UserLogs.uid],
                resultRow[UserLogs.type],
                resultRow[UserLogs.objectId],
                resultRow[UserLogs.objectType]
            )
        }
    }
}

suspend fun DatabaseFactory.addUserLog(log: UserLog, backend: Backend): Result<Unit> {
    return dbQuery(backend) {
        check(UserLogs.insert {
            it[id] = log.id
            it[uid] = log.uid
            it[type] = log.type
            it[objectId] = log.objectId
            it[objectType] = log.objectType
            it[createdTime] = log.createdTime
        }.insertedCount > 0) {
            "Insert user log failed"
        }
    }
}
