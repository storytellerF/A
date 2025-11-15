package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.TitleDatabase
import com.storyteller_f.a.backend.core.types.RawTitle
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildTitleSearchQuery
import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.selectAll

class ExposedTitleDatabase(val exposedDatabaseSession: ExposedDatabaseSession) : TitleDatabase {
    override suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType?,
        scopeId: PrimaryKey?
    ) = getTitleListByPredicate {
        buildTitleSearchQuery(searchType, uid, type, scopeId)
            .bindPaginationQuery(Titles, primaryKeyFetch)
    }.mapResult { list ->
        getTitleCountByPredicate {
            buildTitleSearchQuery(searchType, uid, type, scopeId)
        }.map { count ->
            PaginationResult(list, count)
        }
    }

    override suspend fun getAllRawTitles(primaryKeyFetch: PrimaryKeyFetch) = exposedDatabaseSession.dbSearch {
        search {
            Titles.selectAll().bindPaginationQuery(Titles, primaryKeyFetch)
        }
        map {
            RawTitle(Title.wrapRow(it))
        }
    }.mapResult { list ->
        getTitleCountByPredicate {
            Titles.selectAll()
        }.map { count ->
            PaginationResult(list, count)
        }
    }

    private suspend fun getTitleListByPredicate(
        queryProvider: () -> Query
    ) = exposedDatabaseSession.dbSearch {
        search {
            queryProvider()
        }
        map {
            RawTitle(Title.wrapRow(it))
        }
    }

    private suspend fun getTitleCountByPredicate(
        queryProvider: () -> Query
    ) = exposedDatabaseSession.dbSearch {
        search {
            queryProvider()
        }
        count()
    }
}
