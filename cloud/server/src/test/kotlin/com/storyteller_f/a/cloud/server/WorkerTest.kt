/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.SubscriptionSentLog
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.client.core.addSubscription
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.client.core.updateUserInfo
import com.storyteller_f.a.cloud.worker.doAcgTask
import com.storyteller_f.a.cloud.worker.doIntroTask
import com.storyteller_f.a.cloud.worker.doSubscriptionTask
import com.storyteller_f.a.cloud.worker.doTitleTask
import com.storyteller_f.result.getOrThrow
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerTest {
    @Test
    fun `acg award survives notification failure`() {
        test {
            val (userId, topicId) =
                attachSession { session ->
                    val community = createCommunityForTest("notification failure", "nf1")
                    val topic = createTopic(ObjectType.COMMUNITY, community.id, "Awarded topic").getOrThrow()
                    session.uid to topic.id
                }.custom

            withWorkerBackend { backend ->
                backend.doAcgTask()

                val userAcg =
                    backend.database.user.getUserAcgByIds(
                        com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch(listOf(userId)),
                    ).getOrThrow().single { it.first == userId }.second
                assertEquals(1L, userAcg)
                val taskRecord = backend.database.user.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).getOrThrow()
                assertEquals(topicId, taskRecord?.objectId)
                assertEquals(true, taskRecord?.isSuccess)
            }
        }
    }

    @Test
    fun `test acg task increases user acg count`() {
        test {
            // 创建用户和多个 topic
            val (userId, topicIds) =
                attachSession { session ->
                    val community = createCommunityForTest("test community", "tc1")

                    // 创建多个 topic 来触发 ACG 任务
                    val ids =
                        listOf(
                            createTopic(ObjectType.COMMUNITY, community.id, "Test topic 1").getOrThrow().id,
                            createTopic(ObjectType.COMMUNITY, community.id, "Test topic 2").getOrThrow().id,
                            createTopic(ObjectType.COMMUNITY, community.id, "Test topic 3").getOrThrow().id,
                        )

                    session.uid to ids
                }.custom

            // 执行 ACG 任务
            withWorkerBackend { backend ->
                backend.createSystemUser(1L)
                backend.doAcgTask()

                // 验证用户的 ACG 是否增加
                val userAcg =
                    backend.database.user.getUserAcgByIds(
                        com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch(listOf(userId)),
                    ).getOrThrow()

                assertTrue(userAcg.isNotEmpty(), "User ACG should be recorded")
                val totalAcg = userAcg.find { it.first == userId }?.second ?: 0L
                assertEquals(topicIds.size.toLong(), totalAcg)
                val taskRecord = backend.database.user.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).getOrThrow()
                assertEquals(topicIds.last(), taskRecord?.objectId)
                backend.assertAcgNotificationCount(userId, topicIds.size)
            }
        }
    }

    @Test
    fun `acg retry fails for missing topic`() =
        test {
            attachSession()
            withWorkerBackend { backend ->
                val retry = buildRetryTaskRecord(TaskRecordType.TOPIC_ACG, -1L)
                backend.database.admin.createTaskRecord(retry).getOrThrow()

                backend.doAcgTask()

                val records =
                    backend.database.admin.getTaskRecords(
                        type = TaskRecordType.TOPIC_ACG,
                        isSuccess = null,
                        failureType = null,
                        fetch = PrimaryKeyFetch(cursor = null, size = 20),
                    ).getOrThrow().list
                assertTrue(records.any { it.objectId == -1L && !it.isSuccess })
                assertEquals(false, records.single { it.id == retry.id }.isRetryRequested)
            }
        }

    @Test
    fun `acg retry awards existing topic`() =
        test {
            val owner =
                attachSession {
                    val community = createCommunityForTest()
                    createTopic(ObjectType.COMMUNITY, community.id, "Retry topic").getOrThrow().id
                }
            withWorkerBackend { backend ->
                backend.createSystemUser(1L)
                backend.doAcgTask()
                val retry = buildRetryTaskRecord(TaskRecordType.TOPIC_ACG, owner.custom)
                backend.database.admin.createTaskRecord(retry).getOrThrow()

                backend.doAcgTask()

                val records =
                    backend.database.admin.getTaskRecords(
                        type = TaskRecordType.TOPIC_ACG,
                        isSuccess = null,
                        failureType = null,
                        fetch = PrimaryKeyFetch(cursor = null, size = 20),
                    ).getOrThrow().list
                assertTrue(records.any { it.objectId == owner.custom && it.isSuccess })
                assertEquals(false, records.single { it.id == retry.id }.isRetryRequested)
                val acg =
                    backend.database.user.getUserAcgByIds(
                        com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch(listOf(owner.uid)),
                    ).getOrThrow().single { it.first == owner.uid }.second
                assertEquals(2L, acg)
            }
        }

    @Test
    fun `intro task records each processed user`() {
        test {
            val userIds =
                (1..3).map {
                    attachSession { session ->
                        session.uid
                    }.custom
                }

            withWorkerBackend { backend ->
                backend.createSystemUser(1L)
                backend.doIntroTask()

                val records =
                    backend.database.admin.getTaskRecords(
                        type = TaskRecordType.INTRO,
                        isSuccess = true,
                        failureType = null,
                        fetch = PrimaryKeyFetch(cursor = null, size = 10),
                    ).getOrThrow().list
                assertEquals(userIds.toSet(), records.map { it.objectId }.toSet())
            }
        }
    }

    @Test
    fun `subscription task resumes across pages`() {
        test {
            val owner =
                attachSession {
                    createCommunityForTest("subscription test", "cm1")
                }
            val subscriberIds =
                (1..12).map { index ->
                    attachSession { session ->
                        updateUserInfo(UpdateUserBody(aid = "user$index")).getOrThrow()
                        addSubscription(NewSubscription(owner.custom.id, ObjectType.COMMUNITY)).getOrThrow()
                        session.uid
                    }.custom
                }
            val topic =
                loginSession(owner) {
                    createTopic(ObjectType.COMMUNITY, owner.custom.id, "Paginated subscription topic").getOrThrow()
                }.custom

            withWorkerBackend { backend ->
                backend.createSystemUser(1L)
                val subscriptions =
                    subscriberIds.map { subscriberId ->
                        backend.database.subscription.getSubscription(subscriberId, owner.custom.id)
                            .getOrThrow()
                            ?: error("subscription not found")
                    }.sortedBy { it.id }
                val firstSubscription = subscriptions.first()
                backend.database.subscription.insertSubscriptionSentLog(
                    SubscriptionSentLog(
                        id = SnowflakeFactory.nextId(),
                        uid = firstSubscription.uid,
                        objectId = topic.id,
                        objectType = ObjectType.TOPIC,
                        subscriptionId = firstSubscription.id,
                        createdTime = now(),
                    ),
                ).getOrThrow()

                backend.doSubscriptionTask()

                val latestSentLog =
                    backend.database.subscription.getLatestSubscriptionSentLog(topic.id).getOrThrow()
                assertEquals(subscriptions.last().id, latestSentLog?.subscriptionId)
                val taskRecord = backend.database.user.getLatestTaskRecord(TaskRecordType.SUBSCRIPTION).getOrThrow()
                assertEquals(topic.id, taskRecord?.objectId)
            }
        }
    }

    @Test
    fun `title task records each processed title`() {
        test {
            val titleIds =
                attachSession { session ->
                    val c = createCommunity(com.storyteller_f.a.api.NewCommunity("test community", "tc1")).getOrThrow()
                    val cId = c.id
                    (1..3).map { index ->
                        createTitle(
                            com.storyteller_f.a.api.NewTitle(
                                name = "Test Title $index",
                                type = com.storyteller_f.shared.model.TitleType.REGULAR,
                                receiver = session.uid,
                                scopeId = cId,
                                scopeType = ObjectType.COMMUNITY,
                                description = "Test title description",
                            ),
                        ).getOrThrow().id
                    }
                }.custom

            withWorkerBackend { backend ->
                backend.createSystemUser(1L)
                backend.doTitleTask()

                val taskRecords =
                    backend.database.admin.getTaskRecords(
                        type = TaskRecordType.TITLE,
                        isSuccess = true,
                        failureType = null,
                        fetch = PrimaryKeyFetch(cursor = null, size = 10),
                    ).getOrThrow().list
                assertEquals(titleIds.toSet(), taskRecords.map { it.objectId }.toSet())
            }
        }
    }

    @Test
    fun `worker tasks persist results on shared backend`() =
        test {
            val owner =
                attachSession {
                    val community = createCommunityForTest()
                    val topic = createTopic(ObjectType.COMMUNITY, community.id, "Worker topic").getOrThrow()
                    val title =
                        createTitle(
                            com.storyteller_f.a.api.NewTitle(
                                "Worker title",
                                com.storyteller_f.shared.model.TitleType.REGULAR,
                                it.uid,
                                community.id,
                                ObjectType.COMMUNITY,
                                "Worker title notification",
                            ),
                        ).getOrThrow()
                    topic.id to title.id
                }
            withWorkerBackend { backend ->
                backend.createSystemUser(1L)
                backend.doAcgTask()
                backend.doIntroTask()
                backend.doSubscriptionTask()
                backend.doTitleTask()

                val expected =
                    mapOf(
                        TaskRecordType.TOPIC_ACG to owner.custom.first,
                        TaskRecordType.INTRO to owner.uid,
                        TaskRecordType.SUBSCRIPTION to owner.custom.first,
                        TaskRecordType.TITLE to owner.custom.second,
                    )
                expected.forEach { (type, objectId) ->
                    val records =
                        backend.database.admin.getTaskRecords(
                            type = type,
                            isSuccess = true,
                            failureType = null,
                            fetch = PrimaryKeyFetch(cursor = null, size = 20),
                        ).getOrThrow().list
                    assertTrue(records.any { it.objectId == objectId }, "Missing successful $type result")
                }
            }
        }
}

private suspend fun Backend.assertAcgNotificationCount(userId: Long, expectedCount: Int) {
    val user =
        database.user.getRawUser(ObjectFetch.IdFetch(userId)).getOrThrow()?.user
    requireNotNull(user)
    val notificationTopics =
        database.topic.getTopicByParentId(
            uid = null,
            primaryKeyFetch = PrimaryKeyFetch(cursor = null, size = 10),
            parentId = user.notificationId,
            pinType = null,
        ).getOrThrow().list
    assertEquals(expectedCount, notificationTopics.size)
}

private suspend fun buildRetryTaskRecord(type: TaskRecordType, objectId: Long): TaskRecord {
    val recordId = SnowflakeFactory.nextId()
    return TaskRecord(
        id = recordId,
        createdTime = now(),
        type = type,
        objectId = objectId,
        failureType = TaskRecordType.UNKNOWN_FAILURE,
        failureReason = "retry test",
        isRetryRequested = true,
    )
}

private suspend fun Backend.createSystemUser(id: Long) {
    val algo = com.storyteller_f.shared.getAlgo(AlgoType.P256)
    val (_, publicKeyPem) = algo.generatePemKeyPair().getOrThrow()
    val publicKey = algo.getDerPublicKeyFromPem(publicKeyPem).getOrThrow()
    database.user.createUser(
        User(
            aid = "System",
            encryptionPublicKey = null,
            publicKey = publicKey,
            address = algo.calcAddress(publicKey).getOrThrow(),
            icon = null,
            nickname = "System",
            id = id,
            createdTime = now(),
            acgAmount = 0L,
            passType = PassType.RAW,
            algoType = AlgoType.P256,
            notificationId = SnowflakeFactory.nextId(),
        ),
    ).getOrThrow()
}
