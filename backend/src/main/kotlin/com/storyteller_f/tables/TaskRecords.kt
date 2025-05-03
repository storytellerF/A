package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

object TaskRecords : BaseTable() {
    val type = enumerationByName<TaskRecordType>("type", 10)
    val processedId = customPrimaryKey("processed_id")

    init {
        index("task-records-main", false, type)
    }
}

class TaskRecord(id: PrimaryKey, createdTime: LocalDateTime, val type: TaskRecordType, val processedId: PrimaryKey) :
    BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): TaskRecord {
            return with(TaskRecords) {
                TaskRecord(
                    resultRow[id],
                    resultRow[createdTime],
                    resultRow[type],
                    resultRow[processedId]
                )
            }
        }
    }
}

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

suspend fun DatabaseFactory.getLatestTaskRecord(backend: Backend, type: TaskRecordType): Result<TaskRecord?> {
    return first(backend, TaskRecord::wrapRow) {
        TaskRecords.selectAll().where {
            TaskRecords.type eq type
        }.orderBy(TaskRecords.id, SortOrder.DESC)
    }
}
