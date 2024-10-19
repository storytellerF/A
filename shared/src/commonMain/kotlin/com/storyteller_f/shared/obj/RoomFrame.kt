package com.storyteller_f.shared.obj

import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface RoomFrame {
    @Serializable
    @SerialName("message")
    data class Message(val newTopic: NewTopic, val encryptedAes: Map<PrimaryKey, String> = emptyMap()) : RoomFrame

    @Serializable
    @SerialName("error")
    data class Error(val error: String) : RoomFrame

    @Serializable
    @SerialName("new-topic-info")
    data class NewTopicInfo(val topicInfo: TopicInfo) : RoomFrame
}
