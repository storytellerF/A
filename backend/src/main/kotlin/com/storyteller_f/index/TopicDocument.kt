package com.storyteller_f.index

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
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
                content.plain,
                topic.parentId,
                topic.parentType.name,
                topic.rootId,
                topic.rootType.name,
                topic.author
            )
        }
    }
}

interface TopicSearchService {
    suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit>

    suspend fun getDocument(idList: List<PrimaryKey>): Result<List<TopicDocument?>>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        word: List<String>?,
        size: Int,
        nextTopicId: PrimaryKey?,
        author: PrimaryKey?,
        root: Pair<PrimaryKey, ObjectType>?,
        parent: Pair<PrimaryKey, ObjectType>?,
    ): Result<PaginationResult<TopicDocument>>
}
