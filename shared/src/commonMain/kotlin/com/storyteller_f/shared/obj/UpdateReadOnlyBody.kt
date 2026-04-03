package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class UpdateReadOnlyBody(
    val readOnly: Boolean
)
