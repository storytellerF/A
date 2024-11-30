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
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.selectAll
import kotlin.collections.map
import kotlin.map

suspend fun searchPublicTopics(
    backend: Backend,
    preTopicId: PrimaryKey?,
    nextTopicId: PrimaryKey?,
    size: Int
): Result<PaginationResult<TopicInfo>?> {
    return DatabaseFactory.mapQuery(Topic::toTopicInfo, Topic::wrapRow) {
        buildPublicTopicQuery(false).bindPaginationQuery(Topics, preTopicId, nextTopicId, size)
    }.mapResult { data ->
        DatabaseFactory.count {
            buildPublicTopicQuery(true)
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

private fun buildPublicTopicQuery(getCount: Boolean): Query {
    return if (getCount) {
        Topics
            .selectAll()
            .where {
                Topics.parentType eq ObjectType.COMMUNITY
            }
    } else {
        Topics
            .select(Topics.fields)
            .where {
                Topics.parentType eq ObjectType.COMMUNITY
            }
    }
}
