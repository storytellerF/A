package com.storyteller_f.a.backend.service.search

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField

data class TopicDocument(
    override val id: PrimaryKey,
    val content: String,
    val rootId: PrimaryKey,
    val rootType: String,
    val parentId: PrimaryKey,
    val parentType: String,
    val author: PrimaryKey
) : PrimaryKeyIdentifiable, LuceneDocument {
    override val objectType = ObjectType.TOPIC

    override fun save(): Document {
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("content", content, Field.Store.YES))
            add(LongField("rootId", rootId, Field.Store.YES))
            add(LongField("parentId", parentId, Field.Store.YES))
            add(StringField("rootType", rootType, Field.Store.YES))
            add(StringField("parentType", parentType, Field.Store.YES))
            add(LongField("author", author, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<TopicDocument> {
        fun fromTopic(topic: Topic, content: TopicContent.Plain): TopicDocument {
            return TopicDocument(
                id = topic.id,
                content = content.plain,
                rootId = topic.rootId,
                rootType = topic.rootType.name,
                parentId = topic.parentId,
                parentType = topic.parentType.name,
                author = topic.author
            )
        }

        override fun restore(
            id: PrimaryKey,
            document: Document
        ): TopicDocument {
            return TopicDocument(
                id,
                document.get("content"),
                rootId = document.get("rootId").toPrimaryKey(),
                rootType = document.get("rootType"),
                parentId = document.get("parentId").toPrimaryKey(),
                parentType = document.get("parentType"),
                author = document.get("author").toPrimaryKey()
            )
        }
    }
}

sealed interface TopicDocumentSearch {
    data class Recommend(val uid: PrimaryKey, val communities: List<PrimaryKey>) :
        TopicDocumentSearch

    data class Topics(val parentId: PrimaryKey, val word: List<String>?) : TopicDocumentSearch
    data object RecommendNotLogin : TopicDocumentSearch
    data class All(val word: List<String>?) : TopicDocumentSearch
    data class CommunityRoot(val word: List<String>?) : TopicDocumentSearch
}

interface TopicSearchService {
    suspend fun saveDocument(documents: List<TopicDocument>): Result<Unit>

    suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        topicDocumentSearch: TopicDocumentSearch = TopicDocumentSearch.All(null),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<TopicDocument>>
}
