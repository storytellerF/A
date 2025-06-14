package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.BaseEntity
import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.backend.service.communityName
import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = customPrimaryKey("icon")
    val poster = customPrimaryKey("poster").index()
    val owner = customPrimaryKey("owner").index()
}

class Community(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val icon: PrimaryKey? = null,
    val poster: PrimaryKey? = null
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
                    row[poster]
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
    val icon: PrimaryKey?,
    val poster: PrimaryKey?,
    val joinedTime: LocalDateTime?,
    val lastRead: Long?,
    val memberCount: Long
)
