package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.tables.UserLog
import com.storyteller_f.tables.UserLogs
import org.jetbrains.exposed.sql.insert

suspend fun ExposedDatabaseSession.insertUserLog(log: UserLog): Result<Unit> {
    return dbQuery {
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
