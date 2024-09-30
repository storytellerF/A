package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.tables.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

suspend fun searchWorld(backend: Backend, prePageToken: OKey?, nextPageToken: ULong?, size: Int): Pair<List<TopicInfo>, Long> {
    val data = DatabaseFactory.query(Topic::toTopicInfo, Topic::wrapRow) {
        val query = Topics
            .select(Topics.fields)
            .where {
                Topics.parentType eq ObjectType.COMMUNITY
            }
        if (nextPageToken != null) {
            query.andWhere {
                Topics.id less nextPageToken
            }
        } else if (prePageToken != null) {
            query.andWhere {
                Topics.id greater prePageToken
            }
        }
        query
            .orderBy(Topics.id, SortOrder.DESC)
            .limit(size)
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
    return Pair(data.mapIndexedNotNull { i, t ->
        topicContents[i]?.let {
            t.copy(content = TopicContent.Plain(it.content))
        }
    }, count)
}

