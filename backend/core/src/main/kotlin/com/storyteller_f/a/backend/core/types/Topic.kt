package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Topic(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val author: PrimaryKey,
    val parentId: PrimaryKey,
    val parentType: ObjectType,
    val rootId: PrimaryKey,
    val rootType: ObjectType,
    val content: ByteArray,
    val isEncrypted: Boolean,
    val isPin: Boolean = false,
    val lastModifiedTime: LocalDateTime? = null,
    val aid: String? = null,
) {
    companion object
}
fun Topic.toTopicInfo(
    commentCount: Long = 0,
    hasComment: Boolean = false,
    reactionCount: Long = 0,
    aidValue: String? = null,
    lastRead: PrimaryKey? = null,
    content: TopicContent,
): TopicInfo {
    return TopicInfo(
        id = id,
        content = content,
        author = author,
        rootId = rootId,
        rootType = rootType,
        parentId = parentId,
        parentType = parentType,
        hasJoined = false,
        createdTime = createdTime,
        commentCount = commentCount,
        reactionCount = reactionCount,
        hasComment = hasComment,
        isEncrypted = isEncrypted,
        isPin = isPin,
        lastModifiedTime = lastModifiedTime,
        extension = null,
        aid = aidValue ?: aid,
        lastRead = lastRead,
    )
}
