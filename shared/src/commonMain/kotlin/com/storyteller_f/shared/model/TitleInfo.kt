package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleType
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TitleInfo(
    override val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val type: TitleType,
    val creator: PrimaryKey,
    val receiver: PrimaryKey,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val name: String,
    val descriptionTopicId: PrimaryKey,
    val extension: Extension? = null
) : Identifiable {
    override val objectType: ObjectType
        get() = ObjectType.TITLE

    @Serializable
    data class Extension(
        val creatorInfo: UserInfo,
        val receiverInfo: UserInfo,
        val descriptionTopicInfo: TopicInfo,
        val communityInfo: CommunityInfo? = null,
        val roomInfo: RoomInfo? = null,
        val userInfo: UserInfo? = null,
        val topicInfo: TopicInfo? = null,
    )
}
