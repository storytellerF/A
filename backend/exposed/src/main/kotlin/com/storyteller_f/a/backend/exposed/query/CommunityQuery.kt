package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.exposed.tables.Communities
import com.storyteller_f.a.backend.exposed.tables.Members
import com.storyteller_f.shared.model.PosterSearch
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere

fun Query.bindPosterSearch(
    hasPosterSearch: PosterSearch?
): Query {
    return when (hasPosterSearch) {
        PosterSearch.HAS_POSTER -> andWhere {
            Communities.poster.isNotNull()
        }

        PosterSearch.NO_POSTER -> andWhere {
            Communities.poster.isNull()
        }

        else -> {
            orderBy(Communities.poster.isNull(), SortOrder.ASC)
        }
    }
}

fun Query.buildCommunitySearchQuery(
    joinSearch: JoinSearch,
    word: String?,
    hasPosterSearch: PosterSearch?
): Query {
    when (joinSearch) {
        is JoinSearch.Joined -> {
            adjustColumnSet {
                join(Members, JoinType.INNER, Communities.id, Members.objectId) {
                    Members.uid eq joinSearch.uid
                }
            }
        }

        else -> {
        }
    }
    if (!word.isNullOrBlank()) {
        andWhere {
            Communities.name like "$word%"
        }
    }
    return bindPosterSearch(hasPosterSearch)
}
