package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.UserStatus
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserStatusBody(
    val status: UserStatus
)
