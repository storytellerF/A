/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UserStatus
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal suspend fun Backend.doTopicModerationTask(reviewer: TopicSafetyReviewer) {
    val result = executeTopicModerationTask(reviewer)
    val failure = result.exceptionOrNull()
    if (failure is CancellationException) throw failure
    if (failure == null) {
        Napier.i(tag = MODERATION_LOG_TAG) {
            "topic moderation task succeeded"
        }
    } else {
        Napier.e(tag = MODERATION_LOG_TAG, throwable = failure) {
            "topic moderation task failed"
        }
    }
    delay(DEFAULT_TASK_DELAY_MILLIS)
}

private suspend fun Backend.executeTopicModerationTask(reviewer: TopicSafetyReviewer): Result<Unit> =
    database.user.getLatestTaskRecord(TaskRecordType.TOPIC_MODERATION).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.processedId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor, TOPIC_BATCH_SIZE))
    }.mapResult { topics ->
        if (topics.isEmpty()) {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "no topic to review"
            }
        } else {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "review ${topics.size} topics"
            }
            processTopicsForModeration(topics, reviewer)
        }
        Result.success(Unit)
    }

private suspend fun Backend.processTopicsForModeration(topics: List<Topic>, reviewer: TopicSafetyReviewer) {
    val publicRoomIds = getPublicRoomIds(topics)
    topics.forEach { topic ->
        if (topic.isReviewable(publicRoomIds)) {
            moderateTopic(topic, reviewer)
        }
        database.admin.createTaskRecord(
            TaskRecord(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                type = TaskRecordType.TOPIC_MODERATION,
                processedId = topic.id,
            ),
        ).getOrThrow()
    }
}

private suspend fun Backend.getPublicRoomIds(topics: List<Topic>): Set<PrimaryKey> {
    val roomIds =
        topics.asSequence()
            .filter { it.rootType == ObjectType.ROOM && !it.isEncrypted }
            .map { it.rootId }
            .distinct()
            .toList()
    if (roomIds.isEmpty()) return emptySet()
    return database.room
        .getRoomList(ObjectListFetch.IdListFetch(roomIds))
        .getOrThrow()
        .filter { it.communityId != null }
        .mapTo(mutableSetOf()) { it.id }
}

internal fun Topic.isReviewable(publicRoomIds: Set<PrimaryKey>): Boolean {
    if (isEncrypted) return false
    return when (rootType) {
        ObjectType.COMMUNITY -> true
        ObjectType.USER -> true
        ObjectType.ROOM -> rootId in publicRoomIds
        ObjectType.TOPIC -> false
        ObjectType.TITLE -> false
        ObjectType.FILE -> false
        ObjectType.PANEL_ACCOUNT -> false
    }
}

private suspend fun Backend.moderateTopic(topic: Topic, reviewer: TopicSafetyReviewer) {
    if (!reviewer.isHarmful(topic.content.decodeToString())) return

    check(database.user.updateUserStatus(topic.author, UserStatus.READ_ONLY).getOrThrow()) {
        "Failed to mark topic author ${topic.author} as read only"
    }
    Napier.w(tag = MODERATION_LOG_TAG) {
        "marked topic author ${topic.author} as read only after reviewing topic ${topic.id}"
    }
}

private const val TOPIC_BATCH_SIZE = 10
private const val DEFAULT_TASK_DELAY_MILLIS = 10_000L
private const val MODERATION_LOG_TAG = "moderation"
