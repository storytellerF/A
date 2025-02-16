package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.DEFAULT_PRIMARY_KEY
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopicInfo(
    override val id: PrimaryKey,
    val content: TopicContent,
    val author: PrimaryKey,
    val rootId: PrimaryKey,
    val rootType: ObjectType,
    val parentId: PrimaryKey,
    val parentType: ObjectType,
    val hasJoined: Boolean,
    val createdTime: LocalDateTime,
    val commentCount: Long,
    val reactionCount: Long,
    val hasComment: Boolean,
    val isPrivate: Boolean,
    val lastModifiedTime: LocalDateTime?,
    val extension: Extension?,
    val aid: String? = null,
) : Identifiable {
    companion object {
        val EMPTY = TopicInfo(
            id = DEFAULT_PRIMARY_KEY,
            content = TopicContent.Nil,
            author = DEFAULT_PRIMARY_KEY,
            rootId = DEFAULT_PRIMARY_KEY,
            rootType = ObjectType.TOPIC,
            parentId = DEFAULT_PRIMARY_KEY,
            parentType = ObjectType.TOPIC,
            hasJoined = false,
            createdTime = now(),
            commentCount = 0,
            reactionCount = 0,
            hasComment = false,
            isPrivate = false,
            lastModifiedTime = now(),
            extension = Extension(UserInfo.EMPTY)
        )
    }

    @Serializable
    data class Extension(val authorInfo: UserInfo)
}

@Serializable
sealed interface TopicContent {
    @Serializable
    @SerialName("nil")
    data object Nil : TopicContent

    @Serializable
    @SerialName("extracted")
    data class Extracted(val plain: String, val list: List<MediaInfo> = emptyList(), val origin: String) : TopicContent

    @Serializable
    @SerialName("plain")
    data class Plain(val plain: String, val list: List<MediaInfo> = emptyList()) : TopicContent

    @Serializable
    @SerialName("encrypted")
    data class Encrypted(val encrypted: String, val encryptedKey: Map<PrimaryKey, String>) : TopicContent

    @Serializable
    @SerialName("decrypted-failed")
    data class DecryptFailed(val message: String) : TopicContent
}
