package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class NewCommunity(val name: String, val aid: String, val icon: PrimaryKey? = null)

@Serializable
data class NewDevice(val endpointUrl: String)

@Serializable
data class NewMedia(val noPrefixName: String)


@Serializable
data class NewReaction(val emoji: String)

@Serializable
data class DeleteReaction(val emoji: String)

@Serializable
data class NewRoom(val name: String, val aid: String, val icon: PrimaryKey? = null, val communityId: PrimaryKey? = null)

@Serializable
data class NewTitle(
    val name: String,
    val type: TitleType,
    val receiver: PrimaryKey,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val description: String,
)

@Serializable
data class NewTopic(val parentType: ObjectType, val parentId: PrimaryKey, val content: String)

@Serializable
data class NewRoomTopic(val parentType: ObjectType, val parentId: PrimaryKey, val content: TopicContent)
