package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.MemberPolicy
import com.storyteller_f.shared.model.NestedMemberInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Community(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val memberPolicy: MemberPolicy,
    val iconId: PrimaryKey? = null,
    val posterId: PrimaryKey? = null,
    val fontId: PrimaryKey? = null,
) {
    companion object
}

fun RawCommunity.toCommunityIfo(
    icon: FileInfo? = null,
    poster: FileInfo? = null,
    font: FileInfo? = null,
) = CommunityInfo(
    community.id,
    community.aid,
    community.name,
    community.owner,
    community.createdTime,
    memberCount ?: 0,
    community.memberPolicy,
    member = member?.let {
        NestedMemberInfo(it.status, it.joinedTime, it.invitedTime,)
    },
    lastRead = lastRead,
    latestTopic = latestTopic,
    icon = icon,
    poster = poster,
    hasPoster = poster != null,
    font = font,
)

data class RawCommunity(
    val community: Community,
    val member: Member?,
    val lastRead: Long?,
    val memberCount: Long? = null,
    val latestTopic: PrimaryKey? = null
) {
    val hasJoined = member != null
}
