package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

data class RoomDocument(
    override val id: PrimaryKey,
    val name: String,
    val aid: String,
    val communityId: PrimaryKey? = null
) :
    PrimaryKeyIdentifiable {
    val objectType: ObjectType = ObjectType.ROOM

    companion object {
        fun fromRoom(room: Room): RoomDocument {
            return RoomDocument(room.id, room.name, room.aid, room.communityId)
        }
    }
}

sealed interface RoomDocumentSearch {
    data class Keyword(
        val words: String,
        val communityId: PrimaryKey? = null,
        val fetch: OffsetFetch
    ) : RoomDocumentSearch
}

interface RoomSearchService {
    suspend fun saveDocument(documents: List<RoomDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        roomDocumentSearch: RoomDocumentSearch
    ): Result<PaginationResult<RoomDocument>>
}

interface RoomSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): RoomSearchService
}
