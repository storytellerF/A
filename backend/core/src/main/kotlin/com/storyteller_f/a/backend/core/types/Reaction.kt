package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

class Reaction(
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val emoji: String,
    val count: Long,
    val lastReactionId: PrimaryKey
) {
    companion object
}
