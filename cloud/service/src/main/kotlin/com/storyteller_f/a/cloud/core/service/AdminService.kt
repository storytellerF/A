package com.storyteller_f.a.cloud.core.service

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.shared.model.PanelOverview

suspend fun Backend.getOverview() = runCatching {
    val userCount = combinedDatabase.userDatabase.getUserCount().getOrThrow()
    val topicCount = combinedDatabase.topicDatabase.getTopicCount().getOrThrow()
    val communityCount = combinedDatabase.communityDatabase.getCommunityCount().getOrThrow()
    val privateRoomCount = combinedDatabase.roomDatabase.getPrivateRoomCount().getOrThrow()
    val publicRoomCount = combinedDatabase.roomDatabase.getPublicRoomCount().getOrThrow()
    val fileCount = combinedDatabase.fileDatabase.getFileCount().getOrThrow()
    val fileVolume = combinedDatabase.fileDatabase.getFileVolume().getOrThrow()
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
