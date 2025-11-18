package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UploadRecordStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class UploadRecordInfo(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val status: UploadRecordStatus,
    val total: Long,
    val progress: Long,
    val name: String,
    val chunkSize: Long
)
