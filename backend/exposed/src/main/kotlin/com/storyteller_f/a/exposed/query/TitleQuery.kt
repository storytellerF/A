package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.tables.Titles
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

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
