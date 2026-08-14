package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor.AscCursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storytellerf.a.cloud.worker.DEFAULT_TASK_OBJECT_FETCH_SIZE
import com.storytellerf.a.cloud.worker.executeTaskObject
import com.storytellerf.a.cloud.worker.getSystemUserId
import io.github.aakira.napier.Napier

suspend fun Backend.doAcgTask(fetchSize: Int = DEFAULT_TASK_OBJECT_FETCH_SIZE) {
    val result = executeAcgTask(fetchSize)
    result.fold(
        onSuccess = {
            Napier.i(tag = ACG_LOG_TAG) {
                "acg task completed"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = ACG_LOG_TAG, throwable = failure) {
                "acg task failed"
            }
        },
    )
}

private suspend fun Backend.executeAcgTask(fetchSize: Int): Result<Unit> =
    database.user.getTaskRecordsToRetry(TaskRecordType.TOPIC_ACG, fetchSize).mapResult { retryRecords ->
        if (retryRecords.isEmpty()) {
            processNextAcgTopics(fetchSize)
        } else {
            retryRecords.forEach { retryRecord ->
                processAcgRetry(retryRecord)
            }
            Result.success(Unit)
        }
    }

private suspend fun Backend.processNextAcgTopics(fetchSize: Int): Result<Unit> =
    database.user.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult { taskRecord ->
        val cursor = AscCursor(taskRecord?.objectId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor = cursor, size = fetchSize))
    }.mapResult { topics ->
        if (topics.isEmpty()) {
            Napier.i(tag = ACG_LOG_TAG) {
                "no more topic"
            }
        } else {
            Napier.i(tag = ACG_LOG_TAG) {
                "process ${topics.size} topics"
            }
            topics.forEach { topic ->
                processAcgTopic(topic, retryRecordId = null)
            }
        }
        Result.success(Unit)
    }

private suspend fun Backend.processAcgRetry(record: TaskRecord) {
    executeTaskObject(
        type = TaskRecordType.TOPIC_ACG,
        objectId = record.objectId,
        retryRecordId = record.id,
        failureType = { TaskRecordType.DATA_ACCESS_FAILURE },
    ) { successRecord ->
        val topic =
            database.topic.getTopic(ObjectFetch.IdFetch(record.objectId)).getOrThrow()
                ?: error("Topic ${record.objectId} not found")
        addAcgForTopic(topic, successRecord)
    }.logAcgResult(record.objectId)
}

private suspend fun Backend.processAcgTopic(topic: Topic, retryRecordId: PrimaryKey?) {
    executeTaskObject(
        type = TaskRecordType.TOPIC_ACG,
        objectId = topic.id,
        retryRecordId = retryRecordId,
        failureType = { TaskRecordType.DATA_ACCESS_FAILURE },
    ) { successRecord ->
        addAcgForTopic(topic, successRecord)
    }.logAcgResult(topic.id)
}

private suspend fun Backend.addAcgForTopic(topic: Topic, successRecord: TaskRecord) {
    val userAcgMap =
        database.user.getUserAcgByIds(IdListFetch(listOf(topic.author))).getOrThrow().associateByPair()
    val assetTransaction =
        userAcgMap[topic.author]?.let { oldAcgAmount ->
            AssetTransaction(
                id = SnowflakeFactory.nextId(),
                uid = topic.author,
                createdTime = now(),
                type = AssetType.ACG,
                before = oldAcgAmount,
                after = oldAcgAmount + 1,
            )
        }
    database.user.addAcgForUser(successRecord, assetTransaction).getOrThrow()
    if (assetTransaction != null) {
        notifyAcgAuthor(topic)
    }
}

private suspend fun Backend.notifyAcgAuthor(topic: Topic) {
    UNIT_RESULT.mapResult {
        val systemUserId = getSystemUserId()
        if (shouldNotifyAcgAuthor(topic.author, systemUserId)) {
            val rawUser =
                database.user.getRawUser(ObjectFetch.IdFetch(topic.author)).getOrThrow()
                    ?: error("User ${topic.author} not found")
            sendTopicToNotificationRoom(
                systemUserId,
                rawUser.user,
                buildAcgNotificationContent(topic.id),
            ).getOrThrow()
        }
        UNIT_RESULT
    }.onFailure { failure ->
        Napier.e(tag = ACG_LOG_TAG, throwable = failure) {
            "failed to notify topic author ${topic.author} about ACG award for ${topic.id}"
        }
    }
}

private fun shouldNotifyAcgAuthor(authorId: PrimaryKey, systemUserId: PrimaryKey): Boolean = authorId != systemUserId

private fun buildAcgNotificationContent(topicId: PrimaryKey): String = "Your topic $topicId earned 1 ACG."

private fun Result<Unit>.logAcgResult(objectId: PrimaryKey) {
    fold(
        onSuccess = {
            Napier.i(tag = ACG_LOG_TAG) {
                "processed topic $objectId"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = ACG_LOG_TAG, throwable = failure) {
                "failed to process topic $objectId"
            }
        },
    )
}

private const val ACG_LOG_TAG = "acg"
