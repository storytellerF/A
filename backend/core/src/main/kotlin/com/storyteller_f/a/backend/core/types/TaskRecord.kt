package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TaskRecordInfo
import com.storyteller_f.shared.model.TaskFailureType
import com.storyteller_f.shared.model.TaskRecordStatus
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class TaskRecord(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val type: TaskRecordType,
    val processedId: PrimaryKey,
    val status: TaskRecordStatus = TaskRecordStatus.SUCCESS,
    val failureType: TaskFailureType? = null,
    val failureReason: String? = null,
    val retryRequested: Boolean = false,
) {
    companion object
}

fun TaskRecord.toTaskRecordInfo(): TaskRecordInfo {
    return TaskRecordInfo(id, createdTime, processedId, type, status, failureType, failureReason, retryRequested)
}
