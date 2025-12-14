package com.storyteller_f.a.backend.core.service

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.OffsetFetch
import com.storyteller_f.a.backend.core.PaginationResult
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
    data class Recommend(val uid: PrimaryKey, val communities: List<PrimaryKey>, val fetch: OffsetFetch) :
        TopicDocumentSearch

    data class Topics(val parentId: PrimaryKey, val word: String, val fetch: OffsetFetch) : TopicDocumentSearch
    data class RecommendNotLogin(val fetch: OffsetFetch) : TopicDocumentSearch
    data class All(val word: String, val fetch: OffsetFetch) : TopicDocumentSearch
    data class AllCommunityRoot(val word: String, val fetch: OffsetFetch) : TopicDocumentSearch
}

interface TopicSearchService {
    suspend fun saveDocument(documents: List<TopicDocument>): Result<Unit>

    suspend fun getDocuments(idList: List<PrimaryKey>): Result<List<TopicDocument?>>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        topicDocumentSearch: TopicDocumentSearch
    ): Result<PaginationResult<TopicDocument>>
}

interface TopicSearchServiceFactory {
    fun match(env: MergedEnv): Boolean
    fun build(env: MergedEnv): TopicSearchService
}
