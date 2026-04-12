package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.CustomImmutableList
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class Pagination<T>(val nextPageToken: T?, val prePageToken: T?, val total: Long)

interface ListResponse<T> {
    val data: CustomImmutableList<T>
    val pagination: Pagination<String>?
}

@Serializable
data class ReactionCursorKey(val count: Long, val reactionId: PrimaryKey)
