package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.CustomImmutableMap
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface RoomFrame {
    @Serializable
    @SerialName("message")
    data class Message(
        val newTopic: NewRoomTopic,
        val encryptedAes: CustomImmutableMap<PrimaryKey, String> = persistentMapOf()
    ) : RoomFrame

    @Serializable
    @SerialName("error")
    data class Error(val error: String) : RoomFrame

    @Serializable
    @SerialName("new-topic-info")
    data class NewTopicInfo(val topicInfo: TopicInfo) : RoomFrame

    @Serializable
    @SerialName("start-call")
    data class StartCall(val roomId: PrimaryKey) : RoomFrame

    @Serializable
    @SerialName("stop-call")
    data class StopCall(val roomId: PrimaryKey) : RoomFrame

    @Serializable
    @SerialName("create-offer")
    data class CreateOffer(val targetUid: PrimaryKey, val roomId: PrimaryKey) : RoomFrame

    @Serializable
    @SerialName("send-offer")
    data class SendOffer(val offer: CustomOffer) : RoomFrame

    @Serializable
    @SerialName("create-answer")
    data class CreateAnswer(val targetUid: PrimaryKey, val offer: CustomOffer) : RoomFrame

    @Serializable
    @SerialName("send-answer")
    data class SendAnswer(val answer: CustomAnswer) : RoomFrame

    @Serializable
    @SerialName("respond-answer")
    data class RespondAnswer(val answer: CustomAnswer) : RoomFrame
}

@Serializable
data class CustomOffer(val offer: String, val roomId: PrimaryKey, val targetUid: PrimaryKey)

@Serializable
data class CustomAnswer(val answer: String, val roomId: PrimaryKey, val targetUid: PrimaryKey)