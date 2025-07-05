package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.BaseEntity
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

fun Table.userLogType(name: String) = enumerationByName<UserLogType>(name, 30)

object UserLogs : BaseTable() {
    val uid = customPrimaryKey("uid")
    val type = userLogType("type")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")

    init {
        index("user-logs-main", false, uid, type, objectId)
    }
}

class UserLog(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val uid: PrimaryKey,
    val type: UserLogType,
    val objectId: PrimaryKey,
    val objectType: ObjectType
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): UserLog {
            return with(UserLogs) {
                UserLog(
                    resultRow[id],
                    resultRow[createdTime],
                    resultRow[uid],
                    resultRow[type],
                    resultRow[objectId],
                    resultRow[objectType]
                )
            }
        }
    }
}
