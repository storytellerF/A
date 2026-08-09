package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
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
import com.storyteller_f.shared.model.TaskConfig
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.setupKmpLogger
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.model.LlmProvider
import com.storytellerf.a.cloud.worker.moderation.KoogTopicSafetyReviewer
import com.storytellerf.a.cloud.worker.moderation.LiteRtTopicSafetyReviewer
import com.storytellerf.a.cloud.worker.moderation.TopicSafetyReviewer
import com.storytellerf.a.cloud.worker.moderation.doTopicModerationTask
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        TopicSafetyReviewerProvider { createTopicSafetyReviewer(backend) }.use { reviewerProvider ->
            Napier.i {
                "worker started"
            }
            val jobs = startWorkerTasks(backend, reviewerProvider)
            registerShutdownHook(jobs)
            jobs.joinAll()
        }
        Napier.i("worker done")
    }
}

internal class TopicSafetyReviewerProvider(private val factory: suspend () -> TopicSafetyReviewer?) :
    AutoCloseable {
    private var reviewer: TopicSafetyReviewer? = null
    private var isInitialized = false

    suspend fun get(): TopicSafetyReviewer? {
        if (!isInitialized) {
            reviewer = factory()
            isInitialized = true
        }
        return reviewer
    }

    override fun close() {
        reviewer?.close()
    }
}

internal suspend fun createTopicSafetyReviewer(backend: Backend): TopicSafetyReviewer? {
    val llmConfigResult = backend.database.getLlmConfig()
    val llmConfig = llmConfigResult.getOrNull()
    if (llmConfig != null) {
        Napier.i(tag = "moderation") {
            "using LLM provider: ${llmConfig.provider}"
        }
        return when (llmConfig.provider) {
            LlmProvider.LITERT_LLM -> {
                val modelPath = llmConfig.modelPath
                    ?: throw IllegalArgumentException("modelPath required for LITERT_LLM provider")
                LiteRtTopicSafetyReviewer.create(java.nio.file.Path.of(modelPath))
            }
            else -> KoogTopicSafetyReviewer.create(llmConfig)
        }
    }

    error(
        "LLM configuration not found in database. " +
            "Please configure an LLM provider in the backend_configs table.",
    )
}

private fun CoroutineScope.startWorkerTasks(
    backend: Backend,
    reviewerProvider: TopicSafetyReviewerProvider,
): List<Job> {
    val jobs =
        listOf(
            launchWorkerTask(backend, TaskRecordType.TOPIC_ACG, "acg") { config ->
                backend.doAcgTask(config.fetchSize)
            },
            launchWorkerTask(backend, TaskRecordType.INTRO, "intro") { config ->
                backend.doIntroTask(config.fetchSize)
            },
            launchWorkerTask(backend, TaskRecordType.SUBSCRIPTION, "subscription") { config ->
                backend.doSubscriptionTask(config.fetchSize)
            },
            launchWorkerTask(backend, TaskRecordType.TITLE, "title") { config ->
                backend.doTitleTask(config.fetchSize)
            },
            launchWorkerTask(backend, TaskRecordType.TOPIC_MODERATION, "topic moderation") { config ->
                reviewerProvider.get()?.let { reviewer ->
                    backend.doTopicModerationTask(reviewer, config.fetchSize)
                }
            },
        )
    return jobs
}

private fun CoroutineScope.launchWorkerTask(
    backend: Backend,
    type: TaskRecordType,
    name: String,
    task: suspend (TaskConfig) -> Unit,
): Job {
    val job =
        launch {
            while (isActive) {
                val configResult = backend.database.getTaskConfig(type)
                executeConfiguredTaskIteration(name, configResult, task)
            }
        }
    return job
}

internal suspend fun executeConfiguredTaskIteration(
    name: String,
    configResult: Result<TaskConfig?>,
    task: suspend (TaskConfig) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
) {
    val failure = configResult.exceptionOrNull()
    if (failure is CancellationException) throw failure
    if (failure != null) {
        Napier.e(tag = "task", throwable = failure) {
            "failed to load $name task configuration"
        }
        wait(TASK_CONFIG_POLL_MILLIS)
        return
    }
    val config = configResult.getOrNull()
    if (config?.isEnabled != true) {
        Napier.d(tag = "task") {
            "$name task is not configured or is disabled"
        }
        wait(TASK_CONFIG_POLL_MILLIS)
        return
    }
    Napier.i(tag = "task") {
        "execute $name task at ${now()}"
    }
    task(config)
    wait(config.waitDurationMillis)
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
    override val database: com.storyteller_f.a.backend.core.CombinedDatabase,
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
        buildExposedDatabase(databaseConnection),
    )
}

internal const val TASK_CONFIG_POLL_MILLIS = 10_000L
