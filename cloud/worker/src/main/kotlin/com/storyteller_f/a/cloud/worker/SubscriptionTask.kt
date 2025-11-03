package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.SubscriptionSentLog
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doSubscriptionTask() {
    database.user.getLatestTaskRecord(TaskRecordType.SUBSCRIPTION).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.processedId ?: 0)
        database.topic.getAllTopics(PrimaryKeyFetch(cursor, 10))
    }.mapResult {
        if (it.list.isEmpty()) {
            Napier.i(tag = "subscription") {
                "no topic to send"
            }
            UNIT_RESULT
        } else {
            Napier.i(tag = "subscription") {
                "send ${it.list.size} topics"
            }
            sendTopicToSubscribers(it.list)
        }
    }.onSuccess {
        delay(10000)
        Napier.i(tag = "subscription") {
            "task success $it"
        }
    }.onFailure {
        delay(10000)
        Napier.i(tag = "subscription", throwable = it) {
            "task failed"
        }
    }
}

suspend fun Backend.sendTopicToSubscribers(topics: List<Topic>): Result<Unit> {
    return runCatching {
        topics.forEach { topic ->
            // 当前对象发送的最新日志
            val topicParentId = topic.parentId
            val content = when (topic.rootType) {
                ObjectType.TOPIC -> "New topic at topic"
                ObjectType.ROOM -> "New topic at room"
                ObjectType.COMMUNITY -> "New topic at community"
                else -> null
            } ?: return@forEach
            val log =
                database.user.getLatestSubscriptionSentLog(topicParentId)
                    .getOrThrow()?.subscriptionId
            val cursor = Cursor.AscCursor(log ?: 0)
            val userSubscriptions =
                database.user.getSubscriptionsByObjectId(topicParentId, PrimaryKeyFetch(cursor, 10))
                    .getOrThrow()
            userSubscriptions.forEach { userSubscription ->
                val rawUser = database.user.getRawUser(ObjectFetch.IdFetch(userSubscription.uid))
                    .getOrThrow() ?: throw Exception("user not found")
                sendTopicToNotificationRoom(userSubscription.uid, rawUser.user, content)
                database.user.insertSubscriptionSentLog(
                    SubscriptionSentLog(
                        SnowflakeFactory.nextId(),
                        userSubscription.uid,
                        topic.id,
                        ObjectType.TOPIC,
                        userSubscription.id,
                        now(),
                    )
                ).getOrThrow()
            }
            if (userSubscriptions.isEmpty()) {
                Napier.i(tag = "subscription") {
                    "no user subscription to send topic ${topic.id}"
                }
            }
            database.admin.createTaskRecord(
                TaskRecord(
                    SnowflakeFactory.nextId(),
                    now(),
                    TaskRecordType.SUBSCRIPTION,
                    topic.id,
                )
            ).getOrThrow()
        }
    }
}
