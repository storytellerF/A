package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.CustomImmutableMap
import com.storyteller_f.shared.type.ObjectType
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
    data class SendOffer(
        val offer: CustomOffer,
        val roomId: PrimaryKey,
        val targetUid: PrimaryKey
    ) : RoomFrame

    @Serializable
    @SerialName("create-answer")
    data class CreateAnswer(
        val targetUid: PrimaryKey,
        val offer: CustomOffer,
        val roomId: PrimaryKey
    ) : RoomFrame

    @Serializable
    @SerialName("send-answer")
    data class SendAnswer(
        val answer: CustomAnswer,
        val roomId: PrimaryKey,
        val targetUid: PrimaryKey
    ) : RoomFrame

    @Serializable
    @SerialName("respond-answer")
    data class RespondAnswer(
        val answer: CustomAnswer,
        val roomId: PrimaryKey,
        val targetUid: PrimaryKey
    ) : RoomFrame

    @Serializable
    @SerialName("send-candidate")
    data class SendCandidate(
        val candidate: CustomCandidate,
        val roomId: PrimaryKey,
        val targetUid: PrimaryKey
    ) : RoomFrame

    @Serializable
    @SerialName("receive-candidate")
    data class ReceiveCandidate(
        val candidate: CustomCandidate,
        val roomId: PrimaryKey,
        val uid: PrimaryKey
    ) : RoomFrame
}

@Serializable
data class CustomCandidate(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val candidate: String,
)

@Serializable
data class CustomOffer(val sdp: String)

@Serializable
data class CustomAnswer(val sdp: String)

@Serializable
data class NewRoomTopic(
    val parentType: ObjectType,
    val parentId: PrimaryKey,
    val content: TopicContent
) {
    val tuple = ObjectTuple(parentId, parentType)
}
