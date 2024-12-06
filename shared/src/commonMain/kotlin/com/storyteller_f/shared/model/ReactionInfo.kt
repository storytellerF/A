package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ReactionInfo(
    val emoji: String,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val createdTime: LocalDateTime?,
    val count: Long,
    val hasReacted: Boolean
)

@Serializable
data class SingleReactionInfo(
    val id: PrimaryKey,
    val emoji: String,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val createdTime: LocalDateTime?,
    val uid: PrimaryKey
)
