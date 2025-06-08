package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.*
import com.storyteller_f.shared.utils.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = communityIcon()
    val poster = communityPoster().index()
    val owner = customPrimaryKey("owner").index()
}

class Community(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val icon: String? = null,
    val poster: String? = null
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
    memberCount: Long,
    joinTime: LocalDateTime?,
    lastRead: PrimaryKey?
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

fun mapCommunityInfo(it: ResultRow): CommunityRawResult {
    val community = Community.wrapRow(it)
    val joinedTime = it.getOrNull(MemberJoins.joinedTime)
    val lastRead = it.getOrNull(UserTopicReads.topicId)
    val communityInfo = community.toCommunityIfo(0, joinedTime, lastRead)
    return CommunityRawResult(communityInfo, community.icon, community.poster)
}

data class CommunityRawResult(val communityInfo: CommunityInfo, val icon: String?, val poster: String?)
