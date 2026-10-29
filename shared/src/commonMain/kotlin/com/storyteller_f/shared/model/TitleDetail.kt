/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectStatus
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TitleDetail(
    val titleInfo: TitleInfo,
    val creatorInfo: UserInfo,
    val receiverInfo: UserInfo,
    val descriptionTopicInfo: TopicDetail,
    val communityInfo: CommunityInfo? = null,
    val roomInfo: RoomInfo? = null,
    val userInfo: UserInfo? = null,
    val topicInfo: TopicDetail? = null,
) : ModelObject {
    override val id: PrimaryKey get() = titleInfo.id
    val createdTime: LocalDateTime get() = titleInfo.createdTime
    val type: TitleType get() = titleInfo.type
    val creator: PrimaryKey get() = titleInfo.creator
    val receiver: PrimaryKey get() = titleInfo.receiver
    val scopeId: PrimaryKey get() = titleInfo.scopeId
    val scopeType: ObjectType get() = titleInfo.scopeType
    val name: String get() = titleInfo.name
    val descriptionTopicId: PrimaryKey get() = titleInfo.descriptionTopicId
    val favoriteId: PrimaryKey? get() = titleInfo.favoriteId
    val subscriptionId: PrimaryKey? get() = titleInfo.subscriptionId
    val titleStatus: TitleWorkStatus get() = titleInfo.titleStatus
    val expiresAt: LocalDateTime? get() = titleInfo.expiresAt
    val status: ObjectStatus get() = titleInfo.status
    val readOnly get() = titleInfo.readOnly
    val expired get() = titleInfo.expired
    override val objectType get() = titleInfo.objectType
}
