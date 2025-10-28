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

class ExposedTitleDatabase(val exposedDatabaseSession: ExposedDatabaseSession) : TitleDatabase {
    override suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType?,
        scopeId: PrimaryKey?
    ) = exposedDatabaseSession.dbSearch {
        search {
            buildTitleSearchQuery(searchType, uid, type, scopeId).bindPaginationQuery(
                Titles,
                primaryKeyFetch
            )
        }
        map {
            RawTitle(Title.wrapRow(it))
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
