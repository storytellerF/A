package com.storyteller_f.tables

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.upsert

object UserTopicReads : Table() {
    val uid = customPrimaryKey("uid")
    val updatedAt = datetime("updated_at")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val topicId = customPrimaryKey("topic_id")
    override val primaryKey: Table.PrimaryKey?
        get() = PrimaryKey(uid, objectId)
}

class UserTopicRead(
    val uid: PrimaryKey,
    val updatedAt: LocalDateTime,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val topicId: PrimaryKey
) {
    companion object {
        fun wrapRow(resultRow: ResultRow) {
            return with(UserTopicReads) {
                UserTopicRead(
                    resultRow[uid],
                    resultRow[updatedAt],
                    resultRow[objectId],
                    resultRow[objectType],
                    resultRow[topicId]
                )
            }
        }
    }
}

suspend fun DatabaseFactory.addReadLog(backend: Backend, userTopicRead: UserTopicRead): Result<Unit> {
    return dbQuery(backend) {
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
