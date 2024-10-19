package com.storyteller_f.tables

import com.storyteller_f.BaseObj
import com.storyteller_f.BaseTable
import com.storyteller_f.objectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.selectAll

object Topics : BaseTable() {
    val author = ulong("author").index()
    val parentId = ulong("parent_id")
    val parentType = objectType("parent_type")
    val rootId = ulong("root_id")
    val rootType = objectType("root_type")
    val lastModifiedTime = datetime("last_modified_time").nullable()

    init {
        index("topic-main", false, parentType, parentId)
        index("topic-root", false, rootType, rootId)
    }
}

class Topic(
    val author: PrimaryKey,
    val parentId: PrimaryKey,
    val parentType: ObjectType,
    val rootId: PrimaryKey,
    val rootType: ObjectType,
    val lastModifiedTime: LocalDateTime?,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Topic {
            return Topic(
                row[Topics.author],
                row[Topics.parentId],
                row[Topics.parentType],
                row[Topics.rootId],
                row[Topics.rootType],
                row[Topics.lastModifiedTime],
                row[Topics.id],
                row[Topics.createdTime]
            )
        }

        fun findById(topicId: PrimaryKey): Topic? {
            return findTopicById(topicId)?.let(::wrapRow)
        }

        fun new(info: Topic): PrimaryKey {
            val newTopicId = Topics.insert {
                it[id] = info.id
                it[author] = info.author
                it[createdTime] = now()
                it[parentType] = info.parentType
                it[parentId] = info.parentId
                it[rootId] = info.rootId
                it[rootType] = info.rootType
            }[Topics.id]
            assert(info.id == newTopicId)
            return newTopicId
        }
    }
}

fun findTopicById(id: PrimaryKey): ResultRow? {
    return Topics.selectAll().where {
        Topics.id eq id
    }.limit(1).firstOrNull()
}
