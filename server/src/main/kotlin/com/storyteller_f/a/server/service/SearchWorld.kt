package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.Topic
import com.storyteller_f.tables.Topics
import com.storyteller_f.types.PaginationResult

suspend fun searchWorld(
    backend: Backend,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int
): Result<PaginationResult<TopicInfo>?> {
    val query = Topics
        .select(Topics.fields)
        .where {
            Topics.parentType eq ObjectType.COMMUNITY
        }
    return DatabaseFactory.mapQuery(Topic::toTopicInfo, Topic::wrapRow) {
        query.bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
    }.mapResult { data ->
        DatabaseFactory.count {
            query
        }.mapResult { count ->
            backend.topicDocumentService.getDocument(data.map {
                it.id
            }).map { value ->
                PaginationResult(data.mapIndexed { i, t ->
                    value[i]?.let {
                        t.copy(content = TopicContent.Plain(it.content))
                    } ?: t
                }, count)
            }
        }
    }
}
