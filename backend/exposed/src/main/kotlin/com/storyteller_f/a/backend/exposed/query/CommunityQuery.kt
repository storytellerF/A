package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.exposed.tables.Communities
import com.storyteller_f.a.backend.exposed.tables.MemberJoins
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.select

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
                join(MemberJoins, JoinType.INNER, Communities.id, MemberJoins.objectId) {
                    MemberJoins.uid eq joinSearch.uid
                }
            }
        }

        is JoinSearch.NotJoined -> {
            where {
                Communities.id notInSubQuery (MemberJoins.select(MemberJoins.objectId).where {
                    MemberJoins.uid eq joinSearch.uid
                })
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

suspend fun createCommunityRoomsRaw(
    id: PrimaryKey,
    ownerUid: PrimaryKey,
    communityAid: String,
    roomIds: List<PrimaryKey>
) {
    batchCreateCommunityRooms(
        listOf(
            "${communityAid}_general" to "General",
            "${communityAid}_lobby" to "Lobby",
            "${communityAid}_support" to "Support"
        ).mapIndexed { i, pair ->
            Room(
                roomIds[i],
                now(),
                pair.first,
                pair.second,
                ownerUid,
                communityId = id
            )
        }
    )
}
