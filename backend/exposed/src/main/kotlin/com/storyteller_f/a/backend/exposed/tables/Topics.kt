package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

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

fun Topic.Companion.wrapRow(row: ResultRow): Topic {
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

fun Topic.Companion.findById(topicId: PrimaryKey) = Topics.selectAll().where {
    Topics.id eq topicId
}

suspend fun Topic.Companion.new(info: Topic) {
    return check(Topics.insert {
        it[id] = info.id
        it[author] = info.author
        it[createdTime] = now()
        it[parentType] = info.parentType
        it[parentId] = info.parentId
        it[rootId] = info.rootId
        it[rootType] = info.rootType
        it[content] = ExposedBlob(info.content)
        it[isEncrypted] = info.isEncrypted
    }.insertedCount > 0) {
        "insert topic failed"
    }
}
