package com.storyteller_f.query

import com.storyteller_f.Backend
import com.storyteller_f.tables.UserLog
import com.storyteller_f.tables.UserLogs
import org.jetbrains.exposed.sql.insert

suspend fun Backend.addUserLog(log: UserLog): Result<Unit> {
    return exposedDatabaseSession.dbQuery {
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
