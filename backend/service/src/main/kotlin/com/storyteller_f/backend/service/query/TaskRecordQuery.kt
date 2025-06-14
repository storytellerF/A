package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.first
import com.storyteller_f.backend.service.tables.TaskRecord
import com.storyteller_f.backend.service.tables.TaskRecords
import com.storyteller_f.shared.type.TaskRecordType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun addTaskRecord(taskRecord: TaskRecord) {
    check(TaskRecords.insert {
        it[TaskRecords.id] = taskRecord.id
        it[TaskRecords.createdTime] = taskRecord.createdTime
        it[TaskRecords.type] = taskRecord.type
        it[TaskRecords.processedId] = taskRecord.processedId
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
        first(TaskRecord.Companion::wrapRow)
    }
}
