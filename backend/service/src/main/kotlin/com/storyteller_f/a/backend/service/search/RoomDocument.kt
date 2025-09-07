package com.storyteller_f.a.backend.service.search

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.TextField

data class RoomDocument(override val id: PrimaryKey, val name: String, val aid: String) :
    PrimaryKeyIdentifiable, LuceneDocument {
    override val objectType: ObjectType = ObjectType.ROOM
    override fun save(): Document {
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("name", name, Field.Store.YES))
            add(TextField("aid", aid, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<RoomDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): RoomDocument {
            return RoomDocument(id, document.get("name"), document.get("aid"))
        }

        fun fromRoom(room: Room): RoomDocument {
            return RoomDocument(room.id, room.name, room.aid)
        }
    }
}

sealed interface RoomDocumentSearch {
    data class Keyword(val words: List<String>? = null) : RoomDocumentSearch
}

interface RoomSearchService {
    suspend fun saveDocument(documents: List<RoomDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        roomDocumentSearch: RoomDocumentSearch = RoomDocumentSearch.Keyword(),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<RoomDocument>>
}
