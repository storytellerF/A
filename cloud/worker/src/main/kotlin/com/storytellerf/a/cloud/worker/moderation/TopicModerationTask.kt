/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
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
import kotlinx.coroutines.delay

internal suspend fun Backend.doTopicModerationTask(reviewer: TopicSafetyReviewer) {
    val result = executeTopicModerationTask(reviewer)
    val failure = result.exceptionOrNull()
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
    database.user.getTaskRecordsToRetry(
        TaskRecordType.TOPIC_MODERATION,
        TASK_OBJECT_FETCH_SIZE,
    ).mapResult { retryRecords ->
        val retryRecord = retryRecords.firstOrNull()
        if (retryRecord != null) {
            retryTopicModeration(retryRecord, reviewer)
            Result.success(Unit)
        } else {
            processNextTopicModeration(reviewer)
        }
    }

private suspend fun Backend.processNextTopicModeration(reviewer: TopicSafetyReviewer): Result<Unit> =
    database.user.getLatestTaskRecord(TaskRecordType.TOPIC_MODERATION).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.objectId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor, TASK_OBJECT_FETCH_SIZE))
    }.mapResult { topics ->
        val topic = topics.firstOrNull()
        if (topic == null) {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "no topic to review"
            }
        } else {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "review topic ${topic.id}"
            }
            processTopicModeration(
                topic = topic,
                publicRoomIds = getPublicRoomIds(listOf(topic)),
                reviewer = reviewer,
                retryRecordId = null,
            )
        }
        Result.success(Unit)
    }

private suspend fun Backend.retryTopicModeration(record: TaskRecord, reviewer: TopicSafetyReviewer) {
    val topic = database.topic.getTopic(ObjectFetch.IdFetch(record.objectId)).getOrThrow()
    if (topic == null) {
        saveTopicModerationFailure(
            record.objectId,
            TopicModerationDataAccessException("Topic ${record.objectId} no longer exists"),
            retryRecordId = record.id,
        )
    } else {
        processTopicModeration(
            topic = topic,
            publicRoomIds = getPublicRoomIds(listOf(topic)),
            reviewer = reviewer,
            retryRecordId = record.id,
        )
    }
}

private suspend fun Backend.processTopicModeration(
    topic: Topic,
    publicRoomIds: Set<PrimaryKey>,
    reviewer: TopicSafetyReviewer,
    retryRecordId: PrimaryKey?,
) {
    val result =
        Result.success(Unit).mapResult {
            if (topic.isReviewable(publicRoomIds)) {
                moderateTopic(topic, reviewer)
            }
            Result.success(Unit)
        }
    result.fold(
        onSuccess = {
            saveTopicModerationRecord(
                TaskRecord(
                    id = SnowflakeFactory.nextId(),
                    createdTime = now(),
                    type = TaskRecordType.TOPIC_MODERATION,
                    objectId = topic.id,
                ),
                retryRecordId,
            )
        },
        onFailure = { failure ->
            saveTopicModerationFailure(topic.id, failure, retryRecordId)
            Napier.e(tag = MODERATION_LOG_TAG, throwable = failure) {
                "topic moderation failed for ${topic.id}"
            }
        },
    )
}

private suspend fun Backend.saveTopicModerationFailure(
    topicId: PrimaryKey,
    throwable: Throwable,
    retryRecordId: PrimaryKey?,
) {
    saveTopicModerationRecord(
        TaskRecord(
            id = SnowflakeFactory.nextId(),
            createdTime = now(),
            type = TaskRecordType.TOPIC_MODERATION,
            objectId = topicId,
            isSuccess = false,
            failureType = throwable.toTaskFailureType(),
            failureReason = throwable.message ?: throwable::class.simpleName,
        ),
        retryRecordId,
    )
}

private suspend fun Backend.saveTopicModerationRecord(record: TaskRecord, retryRecordId: PrimaryKey?) {
    database.admin.createTaskRecord(record).getOrThrow()
    retryRecordId?.let { database.user.updateTaskRecordRetryRequested(it, false).getOrThrow() }
}

internal fun Throwable.toTaskFailureType(): String {
    val failureType =
        when (this) {
            is UnexpectedTopicSafetyDecisionException -> TaskRecordType.MODEL_RESPONSE_FAILURE
            is TopicModerationDataAccessException -> TaskRecordType.DATA_ACCESS_FAILURE
            else -> TaskRecordType.MODEL_EXECUTION_FAILURE
        }
    return failureType
}

internal class TopicModerationDataAccessException(message: String) : IllegalStateException(message)

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

private const val TASK_OBJECT_FETCH_SIZE = 1
private const val DEFAULT_TASK_DELAY_MILLIS = 10_000L
private const val MODERATION_LOG_TAG = "moderation"
