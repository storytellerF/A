package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ReactionCursorKey(val count: Long, val reactionId: PrimaryKey)
