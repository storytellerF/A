package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.MemberStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Member(
    val id: PrimaryKey,
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val createdTime: LocalDateTime,
    val status: MemberStatus,
    val joinedTime: LocalDateTime? = null,
    val invitedTime: LocalDateTime? = null,
) {
    companion object
}
