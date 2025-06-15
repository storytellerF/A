package com.storyteller_f.a.exposed

import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.a.exposed.query.bindPaginationQuery
import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.count
import com.storyteller_f.backend.service.query.buildTitleSearchQuery
import com.storyteller_f.backend.service.tables.Title
import com.storyteller_f.backend.service.tables.Titles
import com.storyteller_f.backend.service.tables.toTitleInfo
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.utils.mapResult

class ExposedTitleDatabase(val exposedDatabaseSession: ExposedDatabaseSession) : TitleDatabase {
    override suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType?,
        scopeId: PrimaryKey?
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
}
