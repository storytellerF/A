package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.shared.model.PanelOverview

suspend fun Backend.getOverview() = runCatching {
    val userCount = database.user.getUserCount().getOrThrow()
    val topicCount = database.topic.getTopicCount().getOrThrow()
    val communityCount = database.community.getCommunityCount().getOrThrow()
    val privateRoomCount = database.room.getPrivateRoomCount().getOrThrow()
    val publicRoomCount = database.room.getPublicRoomCount().getOrThrow()
    val fileCount = database.file.getFileCount().getOrThrow()
    val fileVolume = database.file.getFileVolume().getOrThrow()
    PanelOverview(
        userCount,
        topicCount,
        communityCount,
        privateRoomCount,
        publicRoomCount,
        fileCount,
        fileVolume
    )
}
