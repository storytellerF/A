package com.storyteller_f.a.server.service

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.backend
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.tables.*

suspend fun searchWorld(): ServerResponse<TopicInfo> {
    val data = DatabaseFactory.dbQuery {
        Topics
            .select(Topics.fields)
            .where {
                Topics.parentType eq ObjectType.COMMUNITY
            }
            .limit(10).map {
                Topic.wrapRow(it).toTopicInfo()
            }
    }
    val topicContents = backend.topicDocumentService.getDocument(data.map {
        it.id
    })
    return ServerResponse(data.mapIndexedNotNull { i, t ->
        topicContents[i]?.let {
            t.copy(content = TopicContent.Plain(it.content))
        }
    }, 10)
}

