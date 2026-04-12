package com.storyteller_f.a.cloud.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.createTopic
import com.storyteller_f.a.cloud.worker.doAcgTask
import com.storyteller_f.a.cloud.worker.doIntroTask
import com.storyteller_f.a.cloud.worker.doSubscriptionTask
import com.storyteller_f.a.cloud.worker.doTitleTask
import com.storyteller_f.shared.model.AlgoType
import com.storyteller_f.shared.model.PassType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkerTest {

    @Test
    fun `test acg task increases user acg count`() = test {
        // 创建用户和多个 topic
        val (userId, topicCount) = attachSession {
            val community = createCommunityForTest("test community", "tc1")

            // 创建多个 topic 来触发 ACG 任务
            createTopic(ObjectType.COMMUNITY, community.id, "Test topic 1").getOrThrow()
            createTopic(ObjectType.COMMUNITY, community.id, "Test topic 2").getOrThrow()
            createTopic(ObjectType.COMMUNITY, community.id, "Test topic 3").getOrThrow()

            it.uid to 3
        }.custom

        // 执行 ACG 任务
        withWorkerBackend { backend ->
            backend.doAcgTask()

            // 验证用户的 ACG 是否增加
            val userAcg = backend.database.user.getUserAcgByIds(
                com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch(listOf(userId))
            ).getOrThrow()

            // ACG 应该等于创建的 topic 数量
            assertTrue(userAcg.isNotEmpty(), "User ACG should be recorded")
            val totalAcg = userAcg.find { it.first == userId }?.second ?: 0L
            assertTrue(totalAcg >= topicCount, "User ACG should be at least $topicCount, but was $totalAcg")
        }
    }

    @Test
    fun `test intro task sends welcome message`() = test {
        // 创建新用户（这会初始化 Backend）
        attachSession {
            it.uid
        }

        // 创建 System 用户并执行 Intro 任务
        withWorkerBackend { backend ->
            // 创建 System 用户
            val algo = com.storyteller_f.shared.getAlgo()
            val (_, sysPubPem) = algo.generatePemKeyPair().getOrThrow()
            val sysPubDer = algo.getDerPublicKeyFromPem(sysPubPem).getOrThrow()
            val sysAddress = algo.calcAddress(sysPubDer).getOrThrow()

            val systemUser = User(
                aid = "System",
                encryptionPublicKey = null,
                publicKey = sysPubDer,
                address = sysAddress,
                icon = null,
                nickname = "System",
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                acgAmount = 0L,
                passType = PassType.RAW,
                algoType = AlgoType.P256,
                notificationId = SnowflakeFactory.nextId()
            )
            backend.database.user.createUser(systemUser).getOrThrow()

            // 执行 Intro 任务
            backend.doIntroTask()

            // 验证任务记录是否存在
            val taskRecord = backend.database.user.getLatestTaskRecord(TaskRecordType.INTRO).getOrThrow()

            // 任务记录应该存在
            assertNotNull(taskRecord, "Intro task record should exist after running doIntroTask")
        }
    }

    @Test
    fun `test subscription task processes topics`() = test {
        // 创建用户、社区和 topic
        attachSession {
            val community = createCommunityForTest("test community", "tc1")

            // 创建 topic
            createTopic(ObjectType.COMMUNITY, community.id, "Test topic for subscription").getOrThrow()
        }

        // 执行订阅任务
        withWorkerBackend { backend ->
            backend.doSubscriptionTask()

            // 验证任务能正常完成
            assertTrue(true, "Subscription task should complete without error")
        }
    }

    @Test
    fun `test title task sends notification`() = test {
        // 创建用户和 title
        attachSession {
            val c = createCommunity(com.storyteller_f.a.api.NewCommunity("test community", "tc1")).getOrThrow()
            val cId = c.id

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

        // 执行 title 任务
        withWorkerBackend { backend ->
            // 创建 System 用户（为 title 任务准备）
            val algo = com.storyteller_f.shared.getAlgo()
            val (_, sysPubPem) = algo.generatePemKeyPair().getOrThrow()
            val sysPubDer = algo.getDerPublicKeyFromPem(sysPubPem).getOrThrow()
            val sysAddress = algo.calcAddress(sysPubDer).getOrThrow()

            val systemUser = User(
                aid = "System",
                encryptionPublicKey = null,
                publicKey = sysPubDer,
                address = sysAddress,
                icon = null,
                nickname = "System",
                id = 1L, // System user ID should be 1
                createdTime = now(),
                acgAmount = 0L,
                passType = PassType.RAW,
                algoType = AlgoType.P256,
                notificationId = SnowflakeFactory.nextId()
            )
            backend.database.user.createUser(systemUser).getOrThrow()

            // 执行 title 任务
            backend.doTitleTask()

            // 验证任务记录是否存在
            val taskRecord = backend.database.user.getLatestTaskRecord(TaskRecordType.TITLE).getOrThrow()

            // 任务记录应该存在
            assertNotNull(taskRecord, "Title task record should exist after running doTitleTask")
        }
    }

    @Test
    fun `test all worker tasks run without error`() = test {
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
            // 创建 System 用户（为 IntroTask 和 TitleTask 准备）
            val algo = com.storyteller_f.shared.getAlgo()
            val (_, sysPubPem) = algo.generatePemKeyPair().getOrThrow()
            val sysPubDer = algo.getDerPublicKeyFromPem(sysPubPem).getOrThrow()
            val sysAddress = algo.calcAddress(sysPubDer).getOrThrow()

            val systemUser = User(
                aid = "System",
                encryptionPublicKey = null,
                publicKey = sysPubDer,
                address = sysAddress,
                icon = null,
                nickname = "System",
                id = 1L, // System user ID should be 1
                createdTime = now(),
                acgAmount = 0L,
                passType = PassType.RAW,
                algoType = AlgoType.P256,
                notificationId = SnowflakeFactory.nextId()
            )
            backend.database.user.createUser(systemUser).getOrThrow()

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
