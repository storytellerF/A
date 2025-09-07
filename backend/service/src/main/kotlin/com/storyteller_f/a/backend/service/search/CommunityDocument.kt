package com.storyteller_f.a.backend.service.search

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.TextField

data class CommunityDocument(
    override val id: PrimaryKey,
    val name: String,
    val aid: String,
    val owner: PrimaryKey,
) : PrimaryKeyIdentifiable, LuceneDocument {
    override val objectType: ObjectType = ObjectType.COMMUNITY
    override fun save(): Document {
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("name", name, Field.Store.YES))
            add(TextField("aid", aid, Field.Store.YES))
            add(LongField("owner", owner, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<CommunityDocument> {
        fun fromCommunity(community: Community): CommunityDocument {
            return CommunityDocument(community.id, community.name, community.aid, community.owner)
        }

        override fun restore(
            id: PrimaryKey,
            document: Document
        ): CommunityDocument {
            return CommunityDocument(
                id,
                document.get("name"),
                document.get("aid"),
                document.get("owner").toPrimaryKey()
            )
        }
    }
}

sealed interface CommunityDocumentSearch {
    data class Keyword(val keyword: List<String>? = null) : CommunityDocumentSearch
}

interface CommunitySearchService {
    suspend fun saveDocument(documents: List<CommunityDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        communityDocumentSearch: CommunityDocumentSearch = CommunityDocumentSearch.Keyword(),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<CommunityDocument>>
}
