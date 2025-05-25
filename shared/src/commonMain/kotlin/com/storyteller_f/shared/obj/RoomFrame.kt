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
}
