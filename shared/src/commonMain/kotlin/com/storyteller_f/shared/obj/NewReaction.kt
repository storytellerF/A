package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class NewReaction(val emoji: String)

@Serializable
data class DeleteReaction(val emoji: String, val objectId: PrimaryKey)
