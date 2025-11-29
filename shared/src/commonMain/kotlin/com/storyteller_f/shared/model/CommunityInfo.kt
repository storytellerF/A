package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CommunityInfo(
    override val id: PrimaryKey,
    val aid: String,
    val name: String,
    val owner: PrimaryKey,
    val createdTime: LocalDateTime,
    val memberCount: Long,
    val memberPolicy: MemberPolicy,
    val icon: FileInfo? = null,
    val poster: FileInfo? = null,
    val member: NestedMemberInfo? = null,
    val extension: Extension? = null,
    val lastRead: PrimaryKey? = null,
    val latestTopic: PrimaryKey? = null,
    val hasPoster: Boolean = poster != null,
    val font: FileInfo? = null,
) : ModelObject {
    val isJoined = member != null
    override val objectType: ObjectType
        get() = ObjectType.COMMUNITY

    @Serializable
    data class Extension(val targetMemberInfo: NestedMemberInfo? = null)

    companion object {
        val EMPTY = CommunityInfo(DEFAULT_PRIMARY_KEY, "", "", DEFAULT_PRIMARY_KEY, now(), 0, MemberPolicy.OPEN)
    }
}

enum class PosterSearch {
    HAS_POSTER, NO_POSTER, UNSPECIFIED
}

enum class MemberPolicy {
    OPEN, INVITE_ONLY
}
