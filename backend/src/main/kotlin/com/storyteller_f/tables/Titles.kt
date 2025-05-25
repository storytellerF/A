package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.PagingFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

fun Table.titleType(name: String) = enumerationByName<TitleType>(name, 10)

fun Table.titleStatus(name: String) = enumerationByName<TitleStatus>(name, 10)

object Titles : BaseTable() {
    val creator = customPrimaryKey("creator").index()
    val receiver = customPrimaryKey("receiver").index()
    val type = titleType("type").index()
    val scopeId = customPrimaryKey("scope_id").index()
    val scopeType = objectType("scope_type")
    val status = titleStatus("title_status")
    val name = titleName().index()
    val descriptionTopicId = customPrimaryKey("description_topic_id")

    init {
        index("creator", false, creator, type, scopeId)
        index("receiver", false, receiver, type, scopeId)
        index("creator-status", false, creator, status, type, scopeId)
        index("receiver-status", false, receiver, status, type, scopeId)
    }
}

class Title(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val name: String,
    val creator: PrimaryKey,
    val receiver: PrimaryKey,
    val type: TitleType,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val status: TitleStatus,
    val descriptionTopicId: PrimaryKey,
) :
    BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Title {
            return Title(
                row[Titles.id],
                row[Titles.createdTime],
                row[Titles.name],
                row[Titles.creator],
                row[Titles.receiver],
                row[Titles.type],
                row[Titles.scopeId],
                row[Titles.scopeType],
                row[Titles.status],
                row[Titles.descriptionTopicId]
            )
        }
    }
}

suspend fun DatabaseFactory.createTitle(
    backend: Backend,
    title: Title,
    topic: Topic,
    description: String
): Result<TitleInfo> {
    return dbQuery(backend) {
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
        backend.topicSearchService.saveDocument(
            listOf(TopicDocument.fromTopic(topic, TopicContent.Plain(description)))
        )
            .getOrThrow()
        title.toTitleInfo()
    }
}

fun Title.toTitleInfo(): TitleInfo {
    return TitleInfo(id, createdTime, type, creator, receiver, scopeId, scopeType, name, descriptionTopicId, null)
}

suspend fun DatabaseFactory.userTitles(
    backend: Backend,
    pagingFetch: PagingFetch,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
): Result<PaginationResult<TitleInfo>> {
    return dbSearch(backend) {
        search {
            buildTitleSearchQuery(searchType, uid, type, scopeId).bindPaginationQuery(Titles, pagingFetch)
        }
        transform {
            map(Title::wrapRow).map { it.toTitleInfo() }
        }
    }.mapResult { list ->
        count(backend) {
            buildTitleSearchQuery(searchType, uid, type, scopeId)
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
