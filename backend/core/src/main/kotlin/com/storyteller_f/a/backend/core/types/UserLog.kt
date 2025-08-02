package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class UserLog(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val uid: PrimaryKey,
    val type: UserLogType,
    val objectId: PrimaryKey,
    val objectType: ObjectType
) {
    companion object
}
