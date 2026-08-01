package com.storyteller_f.a.cloud.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.backend.core.Backend
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

                val missingTopicId = topicIds.first() - 1
                val missingTopicRetry = buildRetryTaskRecord(TaskRecordType.TOPIC_ACG, missingTopicId)
                val existingTopicRetry = buildRetryTaskRecord(TaskRecordType.TOPIC_ACG, topicIds.first())
                backend.database.admin.createTaskRecord(missingTopicRetry).getOrThrow()
                backend.database.admin.createTaskRecord(existingTopicRetry).getOrThrow()

                backend.doAcgTask()

                val taskRecords =
                    backend.database.admin.getTaskRecords(
                        type = TaskRecordType.TOPIC_ACG,
                        isSuccess = null,
                        failureType = null,
                        fetch = PrimaryKeyFetch(cursor = null, size = 20),
                    ).getOrThrow().list
                assertTrue(taskRecords.any { it.objectId == missingTopicId && !it.isSuccess })
                assertTrue(taskRecords.any { it.objectId == topicIds.first() && it.isSuccess })
                assertEquals(false, taskRecords.single { it.id == missingTopicRetry.id }.isRetryRequested)
                assertEquals(false, taskRecords.single { it.id == existingTopicRetry.id }.isRetryRequested)

                val retriedUserAcg =
                    backend.database.user.getUserAcgByIds(
                        com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch(listOf(userId)),
                    ).getOrThrow().find { it.first == userId }?.second
                assertEquals(topicIds.size + 1L, retriedUserAcg)
            }
        }
    }

    @Test
    fun `test intro task sends welcome message`() {
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
    fun `test title task sends notification`() {
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
    fun `test all worker tasks run without error`() {
        test {
            // 创建测试数据（这会初始化 Backend）
            attachSession {
                val c = createCommunity(com.storyteller_f.a.api.NewCommunity("test community", "tc1")).getOrThrow()
                val cId = c.id
                createTopic(ObjectType.COMMUNITY, cId, "Test topic").getOrThrow()

                // 创建 title
                createTitle(com.storyteller_f.a.api.NewTitle(
                    "Test Title",
                    com.storyteller_f.shared.model.TitleType.REGULAR,
                    it.uid,
                    cId,
                    ObjectType.COMMUNITY,
                    "Test title description"
                )).getOrThrow()
            }

            // 依次执行所有 worker 任务
            withWorkerBackend { backend ->
                backend.createSystemUser(1L)

                // 执行 ACG 任务
                backend.doAcgTask()

                // 执行 Intro 任务
                backend.doIntroTask()

                // 执行 Subscription 任务
                backend.doSubscriptionTask()

                // 执行 Title 任务
                backend.doTitleTask()

                // 验证所有任务都能正常完成
                assertTrue(true, "All worker tasks should complete without error")
            }
        }
    }
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
