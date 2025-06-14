package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.map
import com.storyteller_f.backend.service.tables.UserTopicRead
import com.storyteller_f.backend.service.tables.UserTopicReads
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

suspend fun ExposedDatabaseSession.addReadLog(userTopicRead: UserTopicRead): Result<Unit> {
    return dbQuery {
        check(UserTopicReads.upsert {
            it[uid] = userTopicRead.uid
            it[updatedAt] = userTopicRead.updatedAt
            it[objectId] = userTopicRead.objectId
            it[objectType] = userTopicRead.objectType
            it[topicId] = userTopicRead.topicId
        }.insertedCount > 0) {
            "log failed"
        }
    }
}

suspend fun ExposedDatabaseSession.getTopicReadList(
    parentIds: List<PrimaryKey>,
    uid: PrimaryKey
) = dbSearch {
    search {
        UserTopicReads.selectAll().where {
            UserTopicReads.uid eq uid and (UserTopicReads.objectId inList parentIds)
        }
    }
    map(UserTopicRead.Companion::wrapRow)
}
