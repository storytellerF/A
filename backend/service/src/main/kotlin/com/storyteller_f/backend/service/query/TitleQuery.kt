package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.bindPaginationQuery
import com.storyteller_f.backend.service.count
import com.storyteller_f.backend.service.tables.Title
import com.storyteller_f.backend.service.tables.Titles
import com.storyteller_f.backend.service.tables.Topic
import com.storyteller_f.backend.service.tables.toTitleInfo
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.PrimaryKeyFetch
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.mapResult
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun insertTitle(title: Title, topic: Topic) {
    check(Titles.insert {
        it[Titles.id] = title.id
        it[Titles.createdTime] = title.createdTime
        it[Titles.name] = title.name
        it[Titles.creator] = title.creator
        it[Titles.receiver] = title.receiver
        it[Titles.type] = title.type
        it[Titles.scopeId] = title.scopeId
        it[Titles.scopeType] = title.scopeType
        it[Titles.status] = title.status
        it[Titles.descriptionTopicId] = title.descriptionTopicId
    }.insertedCount > 0) {
        "insert title failed"
    }
    Topic.Companion.new(topic)
}

suspend fun ExposedDatabaseSession.getTitlePaginationResult(
    primaryKeyFetch: PrimaryKeyFetch,
    uid: PrimaryKey,
    searchType: TitleSearchType,
    type: TitleType? = null,
    scopeId: PrimaryKey? = null
): Result<PaginationResult<TitleInfo>> {
    return dbSearch {
        search {
            buildTitleSearchQuery(searchType, uid, type, scopeId).bindPaginationQuery(Titles, primaryKeyFetch)
        }
        transform {
            map(Title.Companion::wrapRow).map { it.toTitleInfo() }
        }
    }.mapResult { list ->
        dbSearch {
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
