package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopicInfo(
    override val id: OKey,
    val content: TopicContent,
    val author: OKey,
    val rootId: OKey,
    val rootType: ObjectType,
    val parentId: OKey,
    val parentType: ObjectType,
    val createdTime: LocalDateTime,
    val lastModifiedTime: LocalDateTime?,
) : Identifiable {
    companion object {
        val EMPTY = TopicInfo(
            0u,
            TopicContent.Plain(""),
            0u,
            0u,
            ObjectType.TOPIC,
            0u,
            ObjectType.TOPIC,
            now(),
            now()
        )
    }
}

@Serializable
sealed interface TopicContent {
    @Serializable
    @SerialName("plain")
    data class Plain(val plain: String) : TopicContent

    @Serializable
    @SerialName("encrypted")
    data class Encrypted(val encrypted: String, val encryptedKey: Map<OKey, String>) : TopicContent

    @Serializable
    @SerialName("decrypted-failed")
    data class DecryptFailed(val message: String) : TopicContent
}
