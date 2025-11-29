package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor.AscCursor
import com.storyteller_f.a.backend.core.ObjectListFetch.IdListFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Topic
import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doAcgTask() {
    database.user.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult { taskRecord ->
        val cursor = AscCursor(taskRecord?.processedId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor = cursor, size = 10))
    }.mapResult { list ->
        if (list.isNotEmpty()) {
            Napier.i(tag = "acg") {
                "topic count ${list.size}"
            }
            acgTask(list)
        } else {
            Napier.i(tag = "acg") {
                "no more topic"
            }
            Result.success(null)
        }
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

private suspend fun Backend.acgTask(list: List<Topic>): Result<Unit> {
    val acgList = list.groupBy {
        it.author
    }.mapValues {
        it.value.count()
    }.toList()
    val uids = acgList.map {
        it.first
    }
    return database.user.getUserAcgByIds(IdListFetch(uids)).map { list ->
        list.associateByPair()
    }.mapResult { userAcgMap ->
        database.user.addAcgForUser(
            TaskRecord(SnowflakeFactory.nextId(), now(), TaskRecordType.TOPIC_ACG, list.last().id),
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
    }
}
