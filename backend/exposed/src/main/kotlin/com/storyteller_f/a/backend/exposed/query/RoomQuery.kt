package com.storyteller_f.a.backend.exposed.query

import com.storyteller_f.a.backend.core.JoinSearch
import com.storyteller_f.a.backend.exposed.tables.Members
import com.storyteller_f.a.backend.exposed.tables.Rooms
import com.storyteller_f.shared.type.PrimaryKey
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere

fun Query.buildRoomSearchWhereQuery(
    joinStatusSearch: JoinSearch,
    community: PrimaryKey?,
    word: String?,
): Query {
    if (community != null) {
        andWhere {
            Rooms.communityId eq community
        }
    }
    if (!word.isNullOrBlank()) {
        andWhere {
            Rooms.name like "%$word%"
        }
    }
    when (joinStatusSearch) {
        is JoinSearch.Joined -> adjustColumnSet {
            this.join(Members, JoinType.INNER, Rooms.id, Members.objectId) {
                Members.uid eq joinStatusSearch.uid
            }
        }

        is JoinSearch.Unspecified -> {
            val uid = joinStatusSearch.uid
            if (uid != null) {
                adjustColumnSet {
                    this.join(Members, JoinType.LEFT, Rooms.id, Members.objectId) {
                        (Members.uid eq uid)
                    }
                }.andWhere {
                    (Members.uid.isNull() and Rooms.communityId.isNotNull()).or(Members.uid.isNotNull())
                }
            } else {
                andWhere {
                    Rooms.communityId.isNotNull()
                }
            }
        }
    }
    return this
}
