package com.storyteller_f.index

import com.storyteller_f.shared.type.PrimaryKey

data class TopicDocument(val id: PrimaryKey, val content: String)

interface TopicDocumentService {
    suspend fun saveDocument(topics: List<TopicDocument>)

    suspend fun getDocument(idList: List<PrimaryKey>): List<TopicDocument?>

    suspend fun clean()
}
