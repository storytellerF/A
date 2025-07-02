package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.BaseEntity
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.communityName
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = customPrimaryKey("icon").nullable()
    val poster = customPrimaryKey("poster").index().nullable()
    val owner = customPrimaryKey("owner").index()
    val fontId = customPrimaryKey("font_id").nullable()
}

class Community(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val iconId: PrimaryKey? = null,
    val posterId: PrimaryKey? = null,
    val fontId: PrimaryKey? = null,
) :
    BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Community {
            return with(Communities) {
                Community(
                    row[id],
                    row[createdTime],
                    row[Aids.value],
                    row[name],
                    row[owner],
                    row[icon],
                    row[poster],
                    row[fontId]
                )
            }
        }
    }
}

fun Community.toCommunityIfo(
    memberCount: Long = 0,
    joinTime: LocalDateTime? = null,
    lastRead: PrimaryKey? = null
) = CommunityInfo(
    id,
    aid,
    name,
    owner,
    createdTime,
    memberCount,
    joinedTime = joinTime,
    lastRead = lastRead
)

data class CommunityRawResult(
    val community: Community,
    val joinedTime: LocalDateTime?,
    val lastRead: Long?,
    val memberCount: Long
)
