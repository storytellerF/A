package com.storyteller_f.a.backend.exposed

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.TitleDatabase
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.core.types.toTitleInfo
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.query.buildTitleSearchQuery
import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import kotlinx.coroutines.flow.toList

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
                buildTitleSearchQuery(searchType, uid, type, scopeId).bindPaginationQuery(
                    Titles,
                    primaryKeyFetch
                )
            }
            transform {
                toList().map {
                    Title.wrapRow(it)
                }.map { it.toTitleInfo() }
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
