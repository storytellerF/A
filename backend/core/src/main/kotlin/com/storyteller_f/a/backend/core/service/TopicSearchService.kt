package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.PrimaryKey

data class TopicDocument(
    override val id: PrimaryKey,
    val content: String,
    val rootId: PrimaryKey,
    val rootType: String,
    val parentId: PrimaryKey,
    val parentType: String,
    val author: PrimaryKey
) : PrimaryKeyIdentifiable {

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

interface TopicSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): TopicSearchService
}
