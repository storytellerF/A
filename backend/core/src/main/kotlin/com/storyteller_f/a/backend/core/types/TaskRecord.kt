package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TaskRecordInfo
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class TaskRecord(private val info: TaskRecordInfo) {
    /** Unique identifier of this task execution. */
    val id: PrimaryKey = info.id

    /** Time when this task execution was recorded. */
    val createdTime: LocalDateTime = info.createdTime

    /** Kind of worker task that produced this execution. */
    val type: TaskRecordType = info.type

    /** Identifier of the business object processed by this execution. */
    val objectId: PrimaryKey = info.objectId

    /** Whether this execution completed successfully. */
    val isSuccess: Boolean = info.isSuccess

    /** Classification of the failure, when the execution failed. */
    val failureType: String? = info.failureType

    /** Safe diagnostic reason for the failure, when available. */
    val failureReason: String? = info.failureReason

    /** Whether an administrator requested this failed execution be retried. */
    val isRetryRequested: Boolean = info.isRetryRequested

    /** Number of successful executions for a task-type summary row. */
    val successCount: Long? = info.successCount

    /** Number of failed executions for a task-type summary row. */
    val failureCount: Long? = info.failureCount

    /** Number of pending retries for a task-type summary row. */
    val retryRequestedCount: Long? = info.retryRequestedCount

    constructor(
        id: PrimaryKey,
        createdTime: LocalDateTime,
        type: TaskRecordType,
        objectId: PrimaryKey,
    ) : this(
        TaskRecordInfo(
            id = id,
            createdTime = createdTime,
            objectId = objectId,
            type = type,
        ),
    )

    constructor(
        id: PrimaryKey,
        createdTime: LocalDateTime,
        type: TaskRecordType,
        objectId: PrimaryKey,
        isSuccess: Boolean,
        failureType: String?,
        failureReason: String?,
    ) : this(
        TaskRecordInfo(
            id = id,
            createdTime = createdTime,
            objectId = objectId,
            type = type,
            isSuccess = isSuccess,
            failureType = failureType,
            failureReason = failureReason,
        ),
    )

    companion object
}

fun TaskRecord.toTaskRecordInfo(): TaskRecordInfo {
    val taskRecordInfo =
        TaskRecordInfo(
            id = id,
            createdTime = createdTime,
            objectId = objectId,
            type = type,
            isSuccess = isSuccess,
            failureType = failureType,
            failureReason = failureReason,
            isRetryRequested = isRetryRequested,
            successCount = successCount,
            failureCount = failureCount,
            retryRequestedCount = retryRequestedCount,
        )
    return taskRecordInfo
}
