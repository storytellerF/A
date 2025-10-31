package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

enum class TaskRecordType {
    TOPIC_ACG, INTRO
}

class TaskRecordInfo(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val processedId: PrimaryKey,
    val type: TaskRecordType
)
