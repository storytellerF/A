package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class UploadRecord(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val total: Long,
    val progress: Long,
    val name: String
) {
    companion object
}