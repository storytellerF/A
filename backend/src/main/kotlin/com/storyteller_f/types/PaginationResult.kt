package com.storyteller_f.types

import com.storyteller_f.shared.type.PrimaryKey

data class PaginationResult<T>(val list: List<T>, val total: Long)

interface Fetch {
    val size: Int
}

sealed interface Cursor<T> {
    data class PreCursor<T>(val value: T) : Cursor<T>
    data class NextCursor<T>(val value: T) : Cursor<T>
}

interface GenericFetch<T> : Fetch {
    val cursor: Cursor<T>?
}

data class PrimaryKeyFetch(override val cursor: Cursor<PrimaryKey>?, override val size: Int) :
    GenericFetch<PrimaryKey>

data class ReactionCursorKey(val count: Long, val reactionId: PrimaryKey)

data class ReactionFetch(override val cursor: Cursor<ReactionCursorKey>?, override val size: Int) :
    GenericFetch<ReactionCursorKey>
