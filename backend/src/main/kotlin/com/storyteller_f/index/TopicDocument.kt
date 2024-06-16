package com.storyteller_f.index

import com.storyteller_f.shared.type.OKey

data class TopicDocument(val id: OKey, val content: String)

interface TopicDocumentService {
     suspend fun saveDocument(topics: List<TopicDocument>)

     suspend fun getDocument(idList: List<OKey>): List<TopicDocument?>

     suspend fun clean()
}
