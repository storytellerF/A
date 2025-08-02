package com.storyteller_f.a.backend.service.index

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.PrimaryKey

data class TopicDocument(
    val id: PrimaryKey,
    val content: String,
    val rootId: PrimaryKey,
    val rootType: String,
    val parentId: PrimaryKey,
    val parentType: String,
    val author: PrimaryKey
) {
    companion object {
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
    }
}

sealed interface DocumentSearch {
    data class Recommend(val uid: PrimaryKey, val communities: List<PrimaryKey>) : DocumentSearch
    data class Topics(val parentId: PrimaryKey) : DocumentSearch
    data object RecommendNotLogin : DocumentSearch
    data object All : DocumentSearch
    data object CommunityRoot : DocumentSearch
}

interface TopicSearchService {
    suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit>

    suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        words: List<String>? = null,
        documentSearch: DocumentSearch = DocumentSearch.All,
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<TopicDocument>>
}
