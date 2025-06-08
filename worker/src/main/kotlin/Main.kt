package com.storyteller_f.worker

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.query.addAcgForUser
import com.storyteller_f.query.getLatestTaskRecord
import com.storyteller_f.query.getTopicList
import com.storyteller_f.query.getUserAcgByIds
import com.storyteller_f.readEnv
import com.storyteller_f.shared.type.TaskRecordType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() {
    val env = readEnv()
    Napier.i {
        "start server at ${env["SERVER_PORT"]}"
    }
    val backend = buildBackendFromEnv(env)
    DatabaseFactory.init(backend)
    runBlocking {
        async {
            while (true) {
                Napier.i(tag = "task") {
                    "execute ${now()}"
                }
                backend.doAcgTask()
            }
        }
    }
}

private suspend fun Backend.doAcgTask() {
    getAcgTaskListFromTopics().mapResultIfNotNull { (acgList, userAcgMap, list) ->
        this.databaseSession.addAcgForUser(acgList, userAcgMap, list)
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
    this.databaseSession.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult {
        this.databaseSession.getTopicList(it?.processedId ?: 0)
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
            this.databaseSession.getUserAcgByIds(uids).map { list ->
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
