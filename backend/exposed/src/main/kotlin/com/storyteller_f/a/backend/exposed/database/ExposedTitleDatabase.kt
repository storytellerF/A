package com.storyteller_f.a.backend.exposed.database

import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.TitleDatabase
import com.storyteller_f.a.backend.core.paginationFromResults
import com.storyteller_f.a.backend.core.types.RawTitle
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.count
import com.storyteller_f.a.backend.exposed.map
import com.storyteller_f.a.backend.exposed.query.bindPaginationQuery
import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.a.backend.exposed.tables.wrapRow
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectStatus
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedTitleDatabase(val exposedDatabaseSession: ExposedDatabaseSession) : TitleDatabase {
    override suspend fun getTitlePaginationResult(
        primaryKeyFetch: PrimaryKeyFetch,
        uid: PrimaryKey,
        searchType: TitleSearchType,
        type: TitleType?,
        scopeId: PrimaryKey?
    ) = paginationFromResults(
        getTitleListByPredicate {
            buildTitleSearchQuery(searchType, uid, type, scopeId)
                .bindPaginationQuery(Titles, primaryKeyFetch)
        },
        getTitleCountByPredicate {
            buildTitleSearchQuery(searchType, uid, type, scopeId)
        }
    )

    fun Query.buildTitleSearchQuery(
        searchType: TitleSearchType,
        uid: PrimaryKey,
        type: TitleType?,
        scopeId: PrimaryKey?
    ): Query {
        val rows = where {
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

    override suspend fun getAllRawTitles(primaryKeyFetch: PrimaryKeyFetch) = paginationFromResults(
        getTitleListByPredicate {
            bindPaginationQuery(Titles, primaryKeyFetch)
        },
        getTitleCountByPredicate()
    )

    override suspend fun getTitleCount() = getTitleCountByPredicate()

    override suspend fun getTitle(id: PrimaryKey) = runCatching {
        val list = exposedDatabaseSession.dbSearch {
            search {
                Titles.selectAll().andWhere { Titles.id eq id }
            }
            map {
                Title.wrapRow(it)
            }
        }.getOrThrow()
        list.firstOrNull()?.let { RawTitle(it) }
    }

    override suspend fun updateTitleStatus(id: PrimaryKey, status: ObjectStatus) = exposedDatabaseSession.dbQuery {
        Titles.update({ Titles.id eq id }) {
            it[Titles.status] = status
        } > 0
    }

    private suspend fun getTitleListByPredicate(
        queryProvider: Query.() -> Query
    ) = exposedDatabaseSession.dbSearch {
        search {
            Titles.selectAll().queryProvider()
        }
        map {
            RawTitle(Title.wrapRow(it))
        }
    }

    private suspend fun getTitleCountByPredicate(
        queryProvider: Query.() -> Query = { this }
    ) = exposedDatabaseSession.dbSearch {
        search {
            Titles.selectAll().queryProvider()
        }
        count()
    }
}
