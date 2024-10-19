package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.tables.*
import org.jetbrains.exposed.sql.selectAll

suspend fun searchWorld(backend: Backend, preTopicId: PrimaryKey?, nextTopicId: ULong?, size: Int) = runCatching {
    val data = DatabaseFactory.mapQuery(Topic::toTopicInfo, Topic::wrapRow) {
        val query = Topics
            .select(Topics.fields)
            .where {
                Topics.parentType eq ObjectType.COMMUNITY
            }
        query.bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
    }
    val count = DatabaseFactory.count {
        Topics
            .selectAll()
            .where {
                Topics.parentType eq ObjectType.COMMUNITY
            }
    }
    val topicContents = backend.topicDocumentService.getDocument(data.map {
        it.id
    })
    Pair(data.mapIndexedNotNull { i, t ->
        topicContents[i]?.let {
            t.copy(content = TopicContent.Plain(it.content))
        }
    }, count)
}
