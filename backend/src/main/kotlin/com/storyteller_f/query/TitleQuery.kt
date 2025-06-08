package com.storyteller_f.query

import com.storyteller_f.Backend
import com.storyteller_f.bindPaginationQuery
import com.storyteller_f.count
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.Title
import com.storyteller_f.tables.Titles
import com.storyteller_f.tables.Topic
import com.storyteller_f.tables.toTitleInfo
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PrimaryKeyFetch
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

suspend fun Backend.createTitle(
    title: Title,
    topic: Topic,
    description: String
): Result<TitleInfo> {
    return exposedDatabaseSession.dbQuery {
        check(Titles.insert {
            it[id] = title.id
            it[createdTime] = title.createdTime
            it[name] = title.name
            it[creator] = title.creator
            it[receiver] = title.receiver
            it[type] = title.type
            it[scopeId] = title.scopeId
            it[scopeType] = title.scopeType
            it[status] = title.status
            it[descriptionTopicId] = title.descriptionTopicId
        }.insertedCount > 0) {
            "insert title failed"
        }
        Topic.new(topic)
        topicSearchService.saveDocument(
            listOf(TopicDocument.fromTopic(topic, TopicContent.Plain(description)))
        )
            .getOrThrow()
        title.toTitleInfo()
    }
}

suspend fun Backend.getTitlePaginationResult(
    primaryKeyFetch: PrimaryKeyFetch,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
): Result<PaginationResult<TitleInfo>> {
    return exposedDatabaseSession.dbSearch {
        search {
            buildTitleSearchQuery(searchType, uid, type, scopeId).bindPaginationQuery(Titles, primaryKeyFetch)
        }
        transform {
            map(Title::wrapRow).map { it.toTitleInfo() }
        }
    }.mapResult { list ->
        exposedDatabaseSession.dbSearch {
            search {
                buildTitleSearchQuery(searchType, uid, type, scopeId)
            }
            count()
        }.map { count ->
            PaginationResult(list, count)
        }
    }
}

private fun buildTitleSearchQuery(
    searchType: TitleSearchType,
    uid: PrimaryKey,
    type: TitleType?,
    scopeId: PrimaryKey?
): Query {
    val rows = Titles.selectAll().where {
        when (searchType) {
            TitleSearchType.CREATOR -> Titles.creator eq uid
            else -> Titles.receiver eq uid
        }
    }
    if (type != null) {
        rows.andWhere {
            Titles.type eq type
        }
    }
    if (scopeId != null) {
        rows.andWhere {
            Titles.scopeId eq scopeId
        }
    }
    return rows
}
