package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.tables.UserLog
import com.storyteller_f.backend.service.tables.UserLogs
import org.jetbrains.exposed.sql.insert

suspend fun ExposedDatabaseSession.insertUserLog(log: UserLog): Result<Unit> {
    return dbQuery {
        check(UserLogs.insert {
            it[UserLogs.id] = log.id
            it[UserLogs.uid] = log.uid
            it[UserLogs.type] = log.type
            it[UserLogs.objectId] = log.objectId
            it[UserLogs.objectType] = log.objectType
            it[UserLogs.createdTime] = log.createdTime
        }.insertedCount > 0) {
            "Insert user log failed"
        }
    }
}
