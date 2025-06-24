package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.CustomImmutableList
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

@Serializable
data class Pagination<T>(val nextPageToken: T?, val prePageToken: T?, val total: Long)

@Serializable
data class ServerResponse<T>(val data: CustomImmutableList<T>, val pagination: Pagination<String>? = null) {
    constructor(list: List<T>, pagination: Pagination<String>? = null) : this(list.toImmutableList(), pagination)
}

@Serializable
data class ReactionCursorKey(val count: Long, val reactionId: PrimaryKey)
