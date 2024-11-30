package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.common.bindPaginationQuery
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.tables.MemberJoins
import com.storyteller_f.tables.User
import com.storyteller_f.tables.Users
import com.storyteller_f.types.PaginationResult
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import kotlin.map

suspend fun searchMembers(
    objectId: PrimaryKey?,
    backend: Backend,
    prePageToken: PrimaryKey?,
    nextPageToken: PrimaryKey?,
    size: Int,
    word: String?
): Result<PaginationResult<UserInfo>> {
    return DatabaseFactory.mapQuery({
        first.toUserInfo() to second
    }, {
        User.wrapRow(it) to it[Users.icon]
    }) {
        buildSearchMembersQuery(objectId, false, word).bindPaginationQuery(
            Users,
            prePageToken,
            nextPageToken,
            size
        )
    }.mapResult { pairs ->
        DatabaseFactory.count {
            buildSearchMembersQuery(objectId, true, word)
        }.mapResult { count ->
            backend.mediaService.get("apic", pairs.map {
                it.second
            }).map { value ->
                PaginationResult(pairs.mapIndexed { index, pair ->
                    pair.first.copy(avatar = value[index]?.let {
                        MediaInfo(it)
                    })
                }, count)
            }
        }
    }
}

private fun buildSearchMembersQuery(objectId: PrimaryKey?, getCount: Boolean, word: String?): Query {
    val query = if (objectId != null) {
        val join = Users.join(MemberJoins, JoinType.INNER, Users.id, MemberJoins.uid)
        if (getCount) {
            join.selectAll()
        } else {
            join.select(Users.fields)
        }.where {
            MemberJoins.objectId eq objectId
        }
    } else {
        Users.selectAll()
    }

    if (!(word.isNullOrBlank())) {
        query.andWhere {
            Users.nickname like "%$word%"
        }
    }
    return query
}
