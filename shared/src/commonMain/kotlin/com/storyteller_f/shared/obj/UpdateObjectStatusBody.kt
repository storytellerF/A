package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.ObjectStatus
import kotlinx.serialization.Serializable

@Serializable
data class UpdateObjectStatusBody(
    val status: ObjectStatus
)
