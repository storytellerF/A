package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PanelOverview(
    val userCount: Long,
    val topicCount: Long,
    val communityCount: Long,
    val privateRoomCount: Long,
    val communityRoomCount: Long,
    val fileCount: Long,
    val fileVolume: Long,
)
