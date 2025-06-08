package com.storyteller_f.query

import com.storyteller_f.Backend
import com.storyteller_f.map
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.UserTopicRead
import com.storyteller_f.tables.UserTopicReads
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

suspend fun Backend.addReadLog(userTopicRead: UserTopicRead): Result<Unit> {
    return exposedDatabaseSession.dbQuery {
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

suspend fun Backend.getTopicReadList(parentIds: List<PrimaryKey>, uid: PrimaryKey) = exposedDatabaseSession.dbSearch {
    search {
        UserTopicReads.selectAll().where {
            UserTopicReads.uid eq uid and (UserTopicReads.objectId inList parentIds)
        }
    }
    map(UserTopicRead::wrapRow)
}
