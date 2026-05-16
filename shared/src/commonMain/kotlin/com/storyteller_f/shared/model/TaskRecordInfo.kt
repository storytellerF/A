package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

enum class TaskRecordType {
    TOPIC_ACG, INTRO, SUBSCRIPTION, TITLE
}

@Serializable
data class TaskRecordInfo(
    override val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val processedId: PrimaryKey,
    val type: TaskRecordType
) : PrimaryKeyIdentifiable
