package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Media(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val name: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val owner: PrimaryKey,
    val ownerType: ObjectType,
    val contentType: String,
    val size: Long,
) {
    val dimension = Dimension(width, height)
    val fullName: String = "$owner/$name"

    companion object
}
