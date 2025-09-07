package com.storyteller_f.a.backend.core

import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.PrimaryKey

sealed interface ObjectFetch {
    data class AidFetch(val aid: String) : ObjectFetch
    data class IdFetch(val id: PrimaryKey) : ObjectFetch
}

sealed interface ObjectListFetch {
    data class AidListFetch(val aidList: List<String>) : ObjectListFetch
    data class IdListFetch(val idList: List<PrimaryKey>) : ObjectListFetch
}

sealed interface Cursor<T> {
    data class PreCursor<T>(val value: T) : Cursor<T>
    data class NextCursor<T>(val value: T) : Cursor<T>
}

interface Fetch {
    val size: Int
}

interface GenericFetch<T> : Fetch {
    val cursor: Cursor<T>?
}

data class PrimaryKeyFetch(override val cursor: Cursor<PrimaryKey>?, override val size: Int) :
    GenericFetch<PrimaryKey>

data class ReactionFetch(override val cursor: Cursor<ReactionCursorKey>?, override val size: Int) :
    GenericFetch<ReactionCursorKey>

fun<T : PrimaryKeyIdentifiable> fixedSort(
    infos: List<T>,
    ids: List<PrimaryKey>
): List<T> {
    val groupBy = infos.associateBy { it.id }
    val processedTopics = ids.mapNotNull {
        groupBy[it]
    }
    return processedTopics
}
