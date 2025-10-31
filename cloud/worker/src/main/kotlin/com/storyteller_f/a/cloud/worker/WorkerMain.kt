package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.buildCommunitySearchService
import com.storyteller_f.a.backend.core.buildNameService
import com.storyteller_f.a.backend.core.buildRoomSearchService
import com.storyteller_f.a.backend.core.buildTopicSearchService
import com.storyteller_f.a.backend.core.buildUserSearchService
import com.storyteller_f.a.backend.core.databaseConnection
import com.storyteller_f.a.backend.core.mediaService
import com.storyteller_f.a.backend.core.readEnv
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.NameService
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.setLogPath
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() {
    setLogPath("A")
    SnowflakeFactory.setMachine(1)
    setupKmpLogger()
    val flavorFilePath = File("../../${BackendConfig.FLAVOR}.env").canonicalPath
    val env = readEnv(flavorFilePath = flavorFilePath)
    Napier.i {
        "start worker"
    }
    val backend = buildBackendFromEnv(env)
    runBlocking {
        val job = launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute agc task at ${now()}"
                }
                backend.doAcgTask()
            }
        }
        listOf(job, launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute intro task at ${now()}"
                }
                backend.doIntroTask()
            }
        })
        // 注册 JVM 关闭钩子，捕获 SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(Thread {
            println("🔻 收到终止信号，准备退出...")
            job.cancel()
            Thread.sleep(1000)
        })
        job.join()
        Napier.i("worker done")
    }
}

class WorkerBackend(
    override val customConfig: CustomConfig,
    override val topicSearchService: TopicSearchService,
    override val roomSearchService: RoomSearchService,
    override val communitySearchService: CommunitySearchService,
    override val userSearchService: UserSearchService,
    override val objectStorageService: ObjectStorageService,
    override val nameService: NameService,
    override val database: CombinedDatabase
) : Backend

fun buildBackendFromEnv(env: MergedEnv): Backend {
    Napier.i("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

    val customConfig = CustomConfig(buildType, flavor, null)

    val topicSearchService = buildTopicSearchService(env)
    val userSearchService = buildUserSearchService(env)
    val roomSearchService = buildRoomSearchService(env)
    val communitySearchService = buildCommunitySearchService(env)
    val mediaService = mediaService(env)

    return WorkerBackend(
        customConfig,
        topicSearchService,
        roomSearchService,
        communitySearchService,
        userSearchService,
        mediaService,
        buildNameService(env),
        buildExposedDatabase(databaseConnection)
    )
}
