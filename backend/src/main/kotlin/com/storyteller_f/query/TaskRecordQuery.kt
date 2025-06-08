package com.storyteller_f.query

import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.first
import com.storyteller_f.shared.type.TaskRecordType
import com.storyteller_f.tables.TaskRecord
import com.storyteller_f.tables.TaskRecords
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun addTaskRecord(taskRecord: TaskRecord) {
    check(TaskRecords.insert {
        it[id] = taskRecord.id
        it[createdTime] = taskRecord.createdTime
        it[type] = taskRecord.type
        it[processedId] = taskRecord.processedId
    }.insertedCount > 0) {
        "Insert task record failed"
    }
}

suspend fun ExposedDatabaseSession.getLatestTaskRecord(type: TaskRecordType): Result<TaskRecord?> {
    return dbSearch {
        search {
            TaskRecords.selectAll().where {
                TaskRecords.type eq type
            }.orderBy(TaskRecords.id, SortOrder.DESC)
        }
        first(TaskRecord::wrapRow)
    }
}
