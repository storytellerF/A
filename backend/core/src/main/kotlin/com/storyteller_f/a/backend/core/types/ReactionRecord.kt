package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class ReactionRecord(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val emoji: String,
    val id: PrimaryKey,
    val createdTime: LocalDateTime
) {
    companion object
}
