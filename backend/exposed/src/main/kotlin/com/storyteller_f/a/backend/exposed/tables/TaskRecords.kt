package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.taskRecordType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.insert

object TaskRecords : BaseTable() {
    val type = taskRecordType("type")
    val processedId = customPrimaryKey("processed_id")

    init {
        index("task-records-main", false, type)
    }
}

fun TaskRecord.Companion.wrapRow(resultRow: ResultRow): TaskRecord {
    return with(TaskRecords) {
        TaskRecord(resultRow[id], resultRow[createdTime], resultRow[type], resultRow[processedId])
    }
}
suspend fun addTaskRecord(taskRecord: TaskRecord) {
    check(TaskRecords.insert {
        it[TaskRecords.id] = taskRecord.id
        it[TaskRecords.createdTime] = taskRecord.createdTime
        it[TaskRecords.type] = taskRecord.type
        it[TaskRecords.processedId] = taskRecord.processedId
    }.insertedCount > 0) {
        "Insert task record failed"
    }
}
