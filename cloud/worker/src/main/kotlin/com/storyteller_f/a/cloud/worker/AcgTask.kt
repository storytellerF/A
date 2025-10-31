package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doAcgTask() {
    getAcgTaskListFromTopics().mapResultIfNotNull { (acgList, userAcgMap, list) ->
        database.user.addAcgForUser(
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
        delay(10000)
        Napier.i(tag = "acg") {
            "task success $it"
        }
    }.onFailure {
        delay(10000)
        Napier.i(tag = "acg", throwable = it) {
            "task failed"
        }
    }
}

private suspend fun Backend.getAcgTaskListFromTopics() =
    database.user.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult { taskRecord ->
        database.topic.getTopicList(PrimaryKeyFetch(taskRecord?.processedId?.let {
            Cursor.PreCursor(it)
        }, 10))
    }.mapResult { list ->
        if (list.isNotEmpty()) {
            Napier.i(tag = "acg") {
                "topic count ${list.size}"
            }
            val acgList = list.groupBy {
                it.author
            }.mapValues {
                it.value.count()
            }.toList()
            val uids = acgList.map {
                it.first
            }
            database.user.getUserAcgByIds(ObjectListFetch.IdListFetch(uids)).map { list ->
                list.associateByPair()
            }.map { userAcgMap ->
                Triple(acgList, userAcgMap, list)
            }
        } else {
            Napier.i(tag = "acg") {
                "no more topic"
            }
            Result.success(null)
        }
    }
