package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.exposed.tables.Titles
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll

fun buildTitleSearchQuery(
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
