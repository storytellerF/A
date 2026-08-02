/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storytellerf.a.cloud.worker.DEFAULT_TASK_OBJECT_FETCH_SIZE
import com.storytellerf.a.cloud.worker.executeTaskObject
import io.github.aakira.napier.Napier

internal suspend fun Backend.doTopicModerationTask(
    reviewer: TopicSafetyReviewer,
    fetchSize: Int = DEFAULT_TASK_OBJECT_FETCH_SIZE,
) {
    val result = executeTopicModerationTask(reviewer, fetchSize)
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
}

private suspend fun Backend.executeTopicModerationTask(reviewer: TopicSafetyReviewer, fetchSize: Int): Result<Unit> =
    database.user.getTaskRecordsToRetry(
        TaskRecordType.TOPIC_MODERATION,
        fetchSize,
    ).mapResult { retryRecords ->
        if (retryRecords.isEmpty()) {
            processNextTopicModeration(reviewer, fetchSize)
        } else {
            retryRecords.forEach { retryRecord ->
                retryTopicModeration(retryRecord, reviewer)
            }
            Result.success(Unit)
        }
    }

private suspend fun Backend.processNextTopicModeration(reviewer: TopicSafetyReviewer, fetchSize: Int): Result<Unit> =
    database.user.getLatestTaskRecord(TaskRecordType.TOPIC_MODERATION).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.objectId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor, fetchSize))
    }.mapResult { topics ->
        if (topics.isEmpty()) {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "no topic to review"
            }
        } else {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "review ${topics.size} topics"
            }
            topics.forEach { topic ->
                processTopicModeration(topic, reviewer, retryRecordId = null)
            }
        }
        Result.success(Unit)
    }

private suspend fun Backend.retryTopicModeration(record: TaskRecord, reviewer: TopicSafetyReviewer) {
    executeTaskObject(
        type = TaskRecordType.TOPIC_MODERATION,
        objectId = record.objectId,
        retryRecordId = record.id,
        failureType = Throwable::toTaskFailureType,
    ) { successRecord ->
        val topic =
            database.topic.getTopic(ObjectFetch.IdFetch(record.objectId)).getOrElse { failure ->
                throw TopicModerationDataAccessException("Failed to load topic ${record.objectId}", failure)
            } ?: throw TopicModerationDataAccessException("Topic ${record.objectId} no longer exists")
        moderateTopicIfRequired(topic, reviewer)
        database.admin.createTaskRecord(successRecord).getOrThrow()
    }.logTopicModerationResult(record.objectId)
}

private suspend fun Backend.processTopicModeration(
    topic: Topic,
    reviewer: TopicSafetyReviewer,
    retryRecordId: PrimaryKey?,
) {
    executeTaskObject(
        type = TaskRecordType.TOPIC_MODERATION,
        objectId = topic.id,
        retryRecordId = retryRecordId,
        failureType = Throwable::toTaskFailureType,
    ) { successRecord ->
        moderateTopicIfRequired(topic, reviewer)
        database.admin.createTaskRecord(successRecord).getOrThrow()
    }.logTopicModerationResult(topic.id)
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

internal class TopicModerationDataAccessException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

private suspend fun Backend.getPublicRoomIds(topic: Topic): Set<PrimaryKey> {
    if (topic.rootType != ObjectType.ROOM || topic.isEncrypted) return emptySet()
    val room =
        database.room.getRawRoom(ObjectFetch.IdFetch(topic.rootId)).getOrElse { failure ->
            throw TopicModerationDataAccessException("Failed to load room ${topic.rootId}", failure)
        }?.room ?: throw TopicModerationDataAccessException("Room ${topic.rootId} not found")
    return if (room.communityId == null) emptySet() else setOf(room.id)
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

private suspend fun Backend.moderateTopicIfRequired(topic: Topic, reviewer: TopicSafetyReviewer) {
    val publicRoomIds = getPublicRoomIds(topic)
    if (topic.isReviewable(publicRoomIds)) {
        moderateTopic(topic, reviewer)
    }
}

private fun Result<Unit>.logTopicModerationResult(objectId: PrimaryKey) {
    fold(
        onSuccess = {
            Napier.i(tag = MODERATION_LOG_TAG) {
                "reviewed topic $objectId"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = MODERATION_LOG_TAG, throwable = failure) {
                "topic moderation failed for $objectId"
            }
        },
    )
}

internal const val MODERATION_LOG_TAG = "moderation"
