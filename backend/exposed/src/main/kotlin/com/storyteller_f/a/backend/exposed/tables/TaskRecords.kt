package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.taskRecordType
import com.storyteller_f.shared.model.TaskRecordInfo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.insert

object TaskRecords : BaseTable() {
    val type = taskRecordType("type")

    /** Identifier of the object processed by the task execution. */
    val objectId: Column<Long> = customPrimaryKey("object_id")

    /** Whether the task execution completed successfully. */
    val success: Column<Boolean> = bool("is_success").default(true)

    /** Optional machine-readable failure category. */
    val failureType: Column<String?> = varchar("failure_type", FAILURE_TYPE_LENGTH).nullable()

    /** Optional safe diagnostic failure reason. */
    val failureReason: Column<String?> = text("failure_reason").nullable()

    /** Whether an administrator requested another execution attempt. */
    val retryRequested: Column<Boolean> = bool("is_retry_requested").default(false)

    init {
        index("task-records-main", false, type)
        index("task-records-filter", false, type, success, failureType, retryRequested)
    }
}

fun TaskRecord.Companion.wrapRow(resultRow: ResultRow): TaskRecord {
    val taskRecord =
        with(TaskRecords) {
            TaskRecord(
                TaskRecordInfo(
                    id = resultRow[id],
                    createdTime = resultRow[createdTime],
                    objectId = resultRow[objectId],
                    type = resultRow[type],
                    isSuccess = resultRow[success],
                    failureType = resultRow[failureType],
                    failureReason = resultRow[failureReason],
                    isRetryRequested = resultRow[retryRequested],
                ),
            )
        }
    return taskRecord
}

suspend fun addTaskRecord(taskRecord: TaskRecord) {
    check(
        TaskRecords.insert { statement ->
            statement[TaskRecords.id] = taskRecord.id
            statement[TaskRecords.createdTime] = taskRecord.createdTime
            statement[TaskRecords.type] = taskRecord.type
            statement[TaskRecords.objectId] = taskRecord.objectId
            statement[TaskRecords.success] = taskRecord.isSuccess
            statement[TaskRecords.failureType] = taskRecord.failureType
            statement[TaskRecords.failureReason] = taskRecord.failureReason
            statement[TaskRecords.retryRequested] = taskRecord.isRetryRequested
        }.insertedCount > 0,
    ) {
        "Insert task record failed"
    }
}

private const val FAILURE_TYPE_LENGTH = 20
