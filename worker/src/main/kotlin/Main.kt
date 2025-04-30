package com.storyteller_f.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.update

fun main() {
    val env = readEnv()
    Napier.i {
        "start server at ${env["SERVER_PORT"]}"
    }
    val backend = buildBackendFromEnv(env)
    DatabaseFactory.init(backend)
    runBlocking {
        async {
            runTask(backend)
        }
    }
}

private suspend fun runTask(backend: Backend) {
    while (true) {
        Napier.i(tag = "task") {
            "execute ${now()}"
        }
        getAcgTaskListFromTopics(backend).mapResultIfNotNull { (acgList, userAcgMap, list) ->
            DatabaseFactory.dbQuery(backend) {
                acgList.forEach { (id, acg) ->
                    userAcgMap[id]?.let { oldAcgAmount ->
                        Users.update({
                            Users.id eq id
                        }) {
                            it[Users.acgAmount] = oldAcgAmount + acg
                        }
                    }
                }

                addTaskRecord(
                    TaskRecord(
                        SnowflakeFactory.nextId(),
                        now(),
                        TaskRecordType.TOPIC_ACG,
                        list.last().id
                    )
                )
            }
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
}

private suspend fun getAcgTaskListFromTopics(
    backend: Backend,
) =
    DatabaseFactory.getLatestTaskRecord(backend, TaskRecordType.TOPIC_ACG).mapResult {
        DatabaseFactory.getRawTopics(backend, it?.processedId ?: 0)
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
            DatabaseFactory.getUserAcgByIds(backend, uids).map { list ->
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
