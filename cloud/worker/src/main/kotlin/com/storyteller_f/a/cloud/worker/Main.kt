package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.MergedEnv
import com.storyteller_f.a.backend.service.databaseConnection
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.readEnv
import com.storyteller_f.a.backend.service.topicDocumentService
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

fun main() {
    Napier.base(kmpLogger)
    val env = readEnv()
    Napier.i {
        "start worker"
    }
    val backend = buildBackendFromEnv(env)
    runBlocking {
        val job = async {
            while (true) {
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
        })
        try {
            job.join()
        } catch (_: Exception) {
            Napier.i("job done")
        }
        Napier.i("worker done")
    }
}

private suspend fun Backend.doAcgTask() {
    getAcgTaskListFromTopics().mapResultIfNotNull { (acgList, userAcgMap, list) ->
        exposedDatabase.userDatabase.addAcgForUser(acgList, userAcgMap, list, SnowflakeFactory.nextId())
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
    exposedDatabase.userDatabase.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult {
        exposedDatabase.topicDatabase.getTopicList(it?.processedId ?: 0)
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
            exposedDatabase.userDatabase.getUserAcgByIds(ObjectListFetch.IdListFetch(uids)).map { list ->
                list.associate {
                    it.first to it.second
                }
            }.map { userAcgMap ->
                Triple(acgList, userAcgMap, list)
            }
        } else {
            Result.success(null)
        }
    }

fun buildBackendFromEnv(env: MergedEnv): Backend {
    println("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

    val customConfig = CustomConfig(buildType, flavor, null)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    return Backend(
        customConfig,
        topicDocumentService,
        mediaService,
        NameService(),
        buildExposedDatabase(databaseConnection)
    )
}
