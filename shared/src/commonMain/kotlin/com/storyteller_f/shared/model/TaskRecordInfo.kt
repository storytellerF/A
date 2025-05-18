package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TaskRecordType
import kotlinx.datetime.LocalDateTime



class TaskRecordInfo(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val processedId: PrimaryKey,
    val type: TaskRecordType
)
