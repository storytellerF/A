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
import com.storyteller_f.a.cloud.ws.api.GlobalWsEventPublisher
import com.storyteller_f.shared.loadCryptoLibIfNeed
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.utils.now
import com.storytellerf.a.cloud.worker.moderation.LiteRtTopicSafetyReviewer
import com.storytellerf.a.cloud.worker.moderation.TopicSafetyReviewer
import com.storytellerf.a.cloud.worker.moderation.doTopicModerationTask
import com.storytellerf.a.cloud.worker.moderation.ensureGemmaModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
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
    val env = readEnv()
    GlobalWsEventPublisher.configure(env["WS_RPC_URL"])
    val backend = buildBackendFromEnv(env)
    runBlocking {
        val modelPath = ensureGemmaModel(env)
        Napier.i {
            "initialize topic moderation model"
        }
        LiteRtTopicSafetyReviewer.create(modelPath).use { reviewer ->
            Napier.i {
                "worker started"
            }
            val jobs = startWorkerTasks(backend, reviewer)
            registerShutdownHook(jobs)
            jobs.joinAll()
        }
        Napier.i("worker done")
    }
}

private fun CoroutineScope.startWorkerTasks(backend: Backend, reviewer: TopicSafetyReviewer): List<Job> {
    val jobs =
        listOf(
            launchWorkerTask("acg") {
                backend.doAcgTask()
            },
            launchWorkerTask("intro") {
                backend.doIntroTask()
            },
            launchWorkerTask("subscription") {
                backend.doSubscriptionTask()
            },
            launchWorkerTask("title") {
                backend.doTitleTask()
            },
            launchWorkerTask("topic moderation") {
                backend.doTopicModerationTask(reviewer)
            },
        )
    return jobs
}

private fun CoroutineScope.launchWorkerTask(name: String, task: suspend () -> Unit): Job {
    val job =
        launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute $name task at ${now()}"
                }
                task()
            }
        }
    return job
}

private fun registerShutdownHook(jobs: List<Job>) {
    val shutdownHook =
        Thread(
            {
                Napier.i {
                    "worker received shutdown signal"
                }
                jobs.forEach(Job::cancel)
            },
            "worker-shutdown",
        )
    Runtime.getRuntime().addShutdownHook(shutdownHook)
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
