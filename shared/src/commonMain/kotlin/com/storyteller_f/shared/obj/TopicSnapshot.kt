package com.storyteller_f.shared.obj

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TopicSnapshot(
    val authorAddress: String?,
    val authorAid: String?,
    val content: String,
    val creatorAddress: String?,
    val creatorAid: String?,
    val topicCreatedTime: LocalDateTime,
    val topicModifiedTime: LocalDateTime?,
    val capturedTime: LocalDateTime,
)

@Serializable
data class TopicSnapshotPack(val snapshot: TopicSnapshot, val hash: String)
