package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.buildCommunitySearchService
import com.storyteller_f.a.backend.core.buildFileSearchService
import com.storyteller_f.a.backend.core.buildMemberSearchService
import com.storyteller_f.a.backend.core.buildNameService
import com.storyteller_f.a.backend.core.buildRoomSearchService
import com.storyteller_f.a.backend.core.buildTopicSearchService
import com.storyteller_f.a.backend.core.buildUserSearchService
import com.storyteller_f.a.backend.core.databaseConnection
import com.storyteller_f.a.backend.core.mediaService
import com.storyteller_f.a.backend.core.readEnv
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.FileSearchService
import com.storyteller_f.a.backend.core.service.MemberSearchService
import com.storyteller_f.a.backend.core.service.NameService
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.setLogPath
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    setLogPath()
    setupKmpLogger()
    loadCryptoLibIfNeed()
    SnowflakeFactory.setMachine(1)
    Napier.i {
        "start worker"
    }
    val backend = buildBackendFromEnv(readEnv())
    runBlocking {
        Napier.i {
            "worker started"
        }
        val jobs = listOf(launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute agc task at ${now()}"
                }
                backend.doAcgTask()
            }
        }, launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute intro task at ${now()}"
                }
                backend.doIntroTask()
            }
        }, launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute subscription task at ${now()}"
                }
                backend.doSubscriptionTask()
            }
        }, launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute title task at ${now()}"
                }
                backend.doTitleTask()
            }
        })
        // 注册 JVM 关闭钩子，捕获 SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(Thread {
            println("🔻 收到终止信号，准备退出...")
            jobs.forEach {
                it.cancel()
            }
            Thread.sleep(1000)
        })
        jobs.forEach {
            it.join()
        }
        Napier.i("worker done")
    }
}

class WorkerBackend(
    override val customConfig: CustomConfig,
    override val topicSearchService: TopicSearchService,
    override val roomSearchService: RoomSearchService,
    override val communitySearchService: CommunitySearchService,
    override val userSearchService: UserSearchService,
    override val memberSearchService: MemberSearchService,
    override val fileSearchService: FileSearchService,
    override val objectStorageService: ObjectStorageService,
    override val nameService: NameService,
    override val database: CombinedDatabase
) : Backend

fun buildBackendFromEnv(env: MergedEnv): Backend {
    Napier.i("load env: ${env.getAll("COMPOSE_PROJECT_NAME")}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

    val customConfig = CustomConfig(buildType, flavor, null)

    val topicSearchService = buildTopicSearchService(env)
    val userSearchService = buildUserSearchService(env)
    val roomSearchService = buildRoomSearchService(env)
    val communitySearchService = buildCommunitySearchService(env)
    val memberSearchService = buildMemberSearchService(env)
    val fileSearchService = buildFileSearchService(env)
    val mediaService = mediaService(env)

    return WorkerBackend(
        customConfig,
        topicSearchService,
        roomSearchService,
        communitySearchService,
        userSearchService,
        memberSearchService,
        fileSearchService,
        mediaService,
        buildNameService(env),
        buildExposedDatabase(databaseConnection)
    )
}
