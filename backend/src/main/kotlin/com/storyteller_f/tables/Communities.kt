package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val aid = varchar("aid", COMMUNITY_ID_LENGTH).uniqueIndex()
    val name = varchar("name", COMMUNITY_NAME_LENGTH).index()
    val icon = varchar("icon", ICON_LENGTH).nullable()
    val owner = ulong("owner").index()
    val poster = varchar("poster", ICON_LENGTH).nullable()
}

class Community(
    val aid: String, val name: String, val icon: String?, val owner: OKey, val poster: String?, id: OKey,
    createdTime: LocalDateTime
) :
    BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Community {
            return Community(
                row[Communities.aid],
                row[Communities.name],
                row[Communities.icon],
                row[Communities.owner],
                row[Communities.poster],
                row[Communities.id],
                row[Communities.createdTime]
            )
        }

        fun find(function: SqlExpressionBuilder.() -> Op<Boolean>): Query {
            return Communities.selectAll().where(function)
        }

        fun findById(id: OKey): Query {
            return Communities.selectAll().where {
                Communities.id eq id
            }
        }

        fun new(community: Community): ULong {
            val id = Communities.insert {
                it[id] = community.id
                it[name] = community.name
                it[owner] = community.owner
                it[aid] = community.aid
                it[createdTime] = community.createdTime
            }[Communities.id]
            assert(id == community.id)
            return id
        }

    }
}

fun findCommunityByAId(aid: String): ResultRow? {
    return Communities.selectAll().where {
        Communities.aid eq aid
    }.limit(1).firstOrNull()
}

fun createCommunityJoin(
    id: OKey,
    community: OKey
) = CommunityJoins.insert {
    it[joinTime] = now()
    it[uid] = id
    it[communityId] = community
}
