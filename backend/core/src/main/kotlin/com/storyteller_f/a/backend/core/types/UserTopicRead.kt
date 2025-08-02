package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class UserTopicRead(
    val uid: PrimaryKey,
    val updatedAt: LocalDateTime,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val topicId: PrimaryKey
) {
    companion object
}
