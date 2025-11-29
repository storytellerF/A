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
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.generateModelMarkdownContent
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doSubscriptionTask() {
    database.user.getLatestTaskRecord(TaskRecordType.SUBSCRIPTION).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.processedId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor, 10))
    }.mapResult {
        if (it.isEmpty()) {
            Napier.i(tag = "subscription") {
                "no topic to send"
            }
            UNIT_RESULT
        } else {
            Napier.i(tag = "subscription") {
                "send ${it.size} topics"
            }
            runCatching {
                it.forEach { topic ->
                    processTopicSubscription(topic)
                }
            }
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

private suspend fun Backend.processTopicSubscription(topic: Topic) {
    // 当前对象发送的最新日志
    val topicParentId = topic.parentId
    val content = generateTopicSubscriptionContent(topic, topicParentId) ?: return
    val log =
        database.subscription.getLatestSubscriptionSentLog(topicParentId)
            .getOrThrow()?.subscriptionId
    val cursor = Cursor.AscCursor(log ?: 0)
    val userSubscriptions =
        database.subscription.getSubscriptionsByObjectId(topicParentId, PrimaryKeyFetch(cursor, 10))
            .getOrThrow()
    userSubscriptions.forEach { userSubscription ->
        val rawUser = database.user.getRawUser(ObjectFetch.IdFetch(userSubscription.uid))
            .getOrThrow() ?: throw Exception("user not found")
        sendTopicToNotificationRoom(1L, rawUser.user, content)
        database.subscription.insertSubscriptionSentLog(
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
        database.admin.createTaskRecord(
            TaskRecord(SnowflakeFactory.nextId(), now(), TaskRecordType.SUBSCRIPTION, topic.id,)
        ).getOrThrow()
    }
}

private fun generateTopicSubscriptionContent(
    topic: Topic,
    topicParentId: PrimaryKey
): String? = when (topic.parentType) {
    ObjectType.TOPIC -> generateTopicSubscriptionContentForTopic(topic.id, topicParentId)

    ObjectType.ROOM -> generateTopicSubscriptionContentForRoom(topic.id, topicParentId)

    ObjectType.COMMUNITY -> generateTopicSubscriptionContentForCommunity(topic.id, topicParentId)

    ObjectType.USER -> generateTopicSubscriptionContentForUser(topic.id, topicParentId)

    else -> null
}

private fun generateTopicSubscriptionContentForUser(
    topicId: PrimaryKey,
    topicParentId: PrimaryKey
) = buildString {
    appendLine("New topic at user")
    appendLine(generateModelMarkdownContent(ObjectTuple(topicId, ObjectType.TOPIC)))
    appendLine(generateModelMarkdownContent(ObjectTuple(topicParentId, ObjectType.USER)))
}

private fun generateTopicSubscriptionContentForCommunity(
    topicId: PrimaryKey,
    topicParentId: PrimaryKey
) = buildString {
    appendLine("New topic at community")
    appendLine(generateModelMarkdownContent(ObjectTuple(topicId, ObjectType.TOPIC)))
    appendLine(generateModelMarkdownContent(ObjectTuple(topicParentId, ObjectType.COMMUNITY)))
}

private fun generateTopicSubscriptionContentForRoom(
    topicId: PrimaryKey,
    topicParentId: PrimaryKey
) = buildString {
    appendLine("New topic at room")
    appendLine(generateModelMarkdownContent(ObjectTuple(topicId, ObjectType.TOPIC)))
    appendLine(generateModelMarkdownContent(ObjectTuple(topicParentId, ObjectType.ROOM)))
}

private fun generateTopicSubscriptionContentForTopic(
    topicId: PrimaryKey,
    topicParentId: PrimaryKey
) = buildString {
    appendLine("New topic at topic")
    appendLine(generateModelMarkdownContent(ObjectTuple(topicId, ObjectType.TOPIC)))
    appendLine(generateModelMarkdownContent(ObjectTuple(topicParentId, ObjectType.TOPIC)))
}
