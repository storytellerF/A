package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Community(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val iconId: PrimaryKey? = null,
    val posterId: PrimaryKey? = null,
    val fontId: PrimaryKey? = null,
) {
    companion object
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

data class RawCommunity(
    val community: Community,
    val joinedTime: LocalDateTime?,
    val lastRead: Long?,
    val memberCount: Long
)
