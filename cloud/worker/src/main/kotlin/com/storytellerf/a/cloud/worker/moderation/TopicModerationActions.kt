/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.a.cloud.worker.sendTopicToNotificationRoom
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UserStatus
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapResult
import com.storytellerf.a.cloud.worker.getSystemUserId
import io.github.aakira.napier.Napier

internal suspend fun Backend.moderateTopic(topic: Topic, reviewer: TopicSafetyReviewer) {
    if (!reviewer.isHarmful(topic.content.decodeToString())) return

    val rawUser = getTopicAuthor(topic)
    updateTopicAuthorStatus(topic)
    notifyTopicAuthor(topic, rawUser)
    Napier.w(tag = MODERATION_LOG_TAG) {
        "marked topic author ${topic.author} as read only after reviewing topic ${topic.id}"
    }
}

private suspend fun Backend.getTopicAuthor(topic: Topic): RawUser =
    database.user.getRawUser(ObjectFetch.IdFetch(topic.author)).getOrElse { failure ->
        throw TopicModerationDataAccessException("Failed to load topic author ${topic.author}", failure)
    } ?: throw TopicModerationDataAccessException("Topic author ${topic.author} not found")

private suspend fun Backend.updateTopicAuthorStatus(topic: Topic) {
    val isUpdated =
        database.user.updateUserStatus(topic.author, UserStatus.READ_ONLY).getOrElse { failure ->
            throw TopicModerationDataAccessException("Failed to update topic author ${topic.author}", failure)
        }
    if (!isUpdated) {
        throw TopicModerationDataAccessException("Topic author ${topic.author} not found")
    }
}

private suspend fun Backend.notifyTopicAuthor(topic: Topic, rawUser: RawUser) {
    UNIT_RESULT.mapResult {
        sendTopicToNotificationRoom(
            getSystemUserId(),
            rawUser.user,
            buildModerationNotificationContent(topic.id),
        )
        UNIT_RESULT
    }.getOrElse { failure ->
        throw TopicModerationDataAccessException("Failed to notify topic author ${topic.author}", failure)
    }
}

internal fun buildModerationNotificationContent(topicId: PrimaryKey): String =
    "Your account was set to read only because topic $topicId contains harmful content."
