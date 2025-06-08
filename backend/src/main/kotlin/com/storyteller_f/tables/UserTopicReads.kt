package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object UserTopicReads : Table() {
    val uid = customPrimaryKey("uid")
    val updatedAt = datetime("updated_at")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val topicId = customPrimaryKey("topic_id")
    override val primaryKey: Table.PrimaryKey?
        get() = PrimaryKey(uid, objectId)

    init {
        index("user-topic-reads-main", true, uid, objectId)
    }
}

class UserTopicRead(
    val uid: PrimaryKey,
    val updatedAt: LocalDateTime,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val topicId: PrimaryKey
) {
    companion object {
        fun wrapRow(resultRow: ResultRow): UserTopicRead {
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
