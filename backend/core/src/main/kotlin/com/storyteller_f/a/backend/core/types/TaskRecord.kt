package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class TaskRecord(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val type: TaskRecordType,
    val processedId: PrimaryKey
) {
    companion object
}
