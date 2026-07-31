package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.taskRecordType
import com.storyteller_f.a.backend.exposed.taskRecordStatus
import com.storyteller_f.a.backend.exposed.taskFailureType
import com.storyteller_f.shared.model.TaskRecordStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.insert

object TaskRecords : BaseTable() {
    val type = taskRecordType("type")
    val objectId = customPrimaryKey("object_id")
    val status = taskRecordStatus("status").default(TaskRecordStatus.SUCCESS)
    val failureType = taskFailureType("failure_type").nullable()
    val failureReason = text("failure_reason").nullable()
    val retryRequested = bool("retry_requested").default(false)

    init {
        index("task-records-main", false, type)
        index("task-records-filter", false, type, status, failureType, retryRequested)
    }
}

fun TaskRecord.Companion.wrapRow(resultRow: ResultRow): TaskRecord {
    return with(TaskRecords) {
        TaskRecord(
            resultRow[id],
            resultRow[createdTime],
            resultRow[type],
            resultRow[objectId],
            resultRow[status],
            resultRow[failureType],
            resultRow[failureReason],
            resultRow[retryRequested],
        )
    }
}
suspend fun addTaskRecord(taskRecord: TaskRecord) {
    check(TaskRecords.insert {
        it[TaskRecords.id] = taskRecord.id
        it[TaskRecords.createdTime] = taskRecord.createdTime
        it[TaskRecords.type] = taskRecord.type
        it[TaskRecords.objectId] = taskRecord.objectId
        it[TaskRecords.status] = taskRecord.status
        it[TaskRecords.failureType] = taskRecord.failureType
        it[TaskRecords.failureReason] = taskRecord.failureReason
        it[TaskRecords.retryRequested] = taskRecord.retryRequested
    }.insertedCount > 0) {
        "Insert task record failed"
    }
}
