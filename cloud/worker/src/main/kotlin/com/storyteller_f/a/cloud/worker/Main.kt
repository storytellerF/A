package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.MergedEnv
import com.storyteller_f.a.backend.service.buildCommunitySearchService
import com.storyteller_f.a.backend.service.buildRoomSearchService
import com.storyteller_f.a.backend.service.buildTopicSearchService
import com.storyteller_f.a.backend.service.buildUserSearchService
import com.storyteller_f.a.backend.service.databaseConnection
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.readEnv
import com.storyteller_f.a.backend.setLogPath
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    setLogPath("A")
    SnowflakeFactory.setMachine(1)
    Napier.base(kmpLogger)
    val env = readEnv()
    Napier.i {
        "start worker"
    }
    val backend = buildBackendFromEnv(env)
    runBlocking {
        val job = launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute ${now()}"
                }
                backend.doAcgTask()
            }
        }
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

private suspend fun Backend.doAcgTask() {
    getAcgTaskListFromTopics().mapResultIfNotNull { (acgList, userAcgMap, list) ->
        combinedDatabase.userDatabase.addAcgForUser(
            TaskRecord(
                SnowflakeFactory.nextId(),
                now(),
                TaskRecordType.TOPIC_ACG,
                list.last().id
            ),
            acgList.mapNotNull { (id, acg) ->
                userAcgMap[id]?.let { oldAcgAmount ->
                    AssetTransaction(
                        SnowflakeFactory.nextId(),
                        id,
                        now(),
                        AssetType.ACG,
                        oldAcgAmount,
                        oldAcgAmount + acg
                    )
                }
            }
        )
    }.onSuccess {
        delay(1000)
        Napier.i(tag = "task") {
            "task success $it"
        }
    }.onFailure {
        delay(1000)
        Napier.i(tag = "task", throwable = it) {
            "task failed"
        }
    }
}

private suspend fun Backend.getAcgTaskListFromTopics() =
    combinedDatabase.userDatabase.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult { taskRecord ->
        combinedDatabase.topicDatabase.getTopicList(PrimaryKeyFetch(taskRecord?.processedId?.let {
            Cursor.PreCursor(it)
        }, 10))
    }.mapResult { list ->
        if (list.isNotEmpty()) {
            val acgList = list.groupBy {
                it.author
            }.mapValues {
                it.value.count()
            }.toList()
            val uids = acgList.map {
                it.first
            }
            combinedDatabase.userDatabase.getUserAcgByIds(ObjectListFetch.IdListFetch(uids))
                .map { list ->
                    list.associateByPair()
                }.map { userAcgMap ->
                    Triple(acgList, userAcgMap, list)
                }
        } else {
            Result.success(null)
        }
    }

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

    return Backend(
        customConfig,
        topicSearchService,
        roomSearchService,
        communitySearchService,
        userSearchService,
        mediaService,
        NameService(),
        buildExposedDatabase(databaseConnection)
    )
}
