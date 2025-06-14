package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.BaseEntity
import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.backend.service.objectType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

object Topics : BaseTable() {
    val author = customPrimaryKey("author").index()
    val parentId = customPrimaryKey("parent_id").index()
    val parentType = objectType("parent_type")
    val rootId = customPrimaryKey("root_id").index()
    val rootType = objectType("root_type")
    val pinned = bool("pinned").default(false)
    val lastModifiedTime = datetime("last_modified_time").nullable()
    val content = blob("content")
    val isEncrypted = bool("is_encrypted")
}

class Topic(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val author: PrimaryKey,
    val parentId: PrimaryKey,
    val parentType: ObjectType,
    val rootId: PrimaryKey,
    val rootType: ObjectType,
    val content: ByteArray,
    val isEncrypted: Boolean,
    val isPin: Boolean = false,
    val lastModifiedTime: LocalDateTime? = null,
    val aid: String? = null,
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Topic {
            return with(Topics) {
                Topic(
                    row[id],
                    row[createdTime],
                    row[author],
                    row[parentId],
                    row[parentType],
                    row[rootId],
                    row[rootType],
                    row[content].bytes,
                    row[isEncrypted],
                    row[pinned],
                    row[lastModifiedTime],
                    row.getOrNull(Aids.value),
                )
            }
        }

        fun findById(topicId: PrimaryKey) = Topics.selectAll().where {
            Topics.id eq topicId
        }

        fun new(info: Topic) {
            return check(Topics.insert {
                it[id] = info.id
                it[author] = info.author
                it[createdTime] = now()
                it[parentType] = info.parentType
                it[parentId] = info.parentId
                it[rootId] = info.rootId
                it[rootType] = info.rootType
                it[content] = ExposedBlob(info.content)
            }.insertedCount > 0) {
                "insert topic failed"
            }
        }
    }
}

fun Topic.toTopicInfo(
    commentCount: Long = 0,
    hasComment: Boolean = false,
    reactionCount: Long = 0,
    aidValue: String? = null,
    lastRead: PrimaryKey? = null,
    content: TopicContent,
): TopicInfo {
    return TopicInfo(
        id = id,
        content = content,
        author = author,
        rootId = rootId,
        rootType = rootType,
        parentId = parentId,
        parentType = parentType,
        hasJoined = false,
        createdTime = createdTime,
        commentCount = commentCount,
        reactionCount = reactionCount,
        hasComment = hasComment,
        isEncrypted = isEncrypted,
        isPin = isPin,
        lastModifiedTime = lastModifiedTime,
        extension = null,
        aid = aidValue ?: aid,
        lastRead = lastRead,
    )
}
