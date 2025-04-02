package com.storyteller_f.index

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.Topic
import com.storyteller_f.types.PaginationResult

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
                topic.id,
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
}

interface TopicSearchService {
    suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit>

    suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        size: Int,
        word: List<String>? = null,
        preTopicId: PrimaryKey? = null,
        nextTopicId: PrimaryKey? = null,
        documentSearch: DocumentSearch = DocumentSearch.All
    ): Result<PaginationResult<TopicDocument>>
}
