package com.storyteller_f.index

import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.types.PaginationResult

data class TopicDocument(val id: PrimaryKey, val content: String)

interface TopicDocumentService {
    suspend fun saveDocument(topics: List<TopicDocument>): Result<Unit>

    suspend fun getDocument(idList: List<PrimaryKey>): Result<List<TopicDocument?>>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        word: List<String>,
        size: Int,
        nextTopicId: PrimaryKey?
    ): Result<PaginationResult<TopicDocument>>
}
