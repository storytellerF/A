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
        val cursor = AscCursor(taskRecord?.objectId ?: 0)
        database.topic.getTopicList(PrimaryKeyFetch(cursor = cursor, size = TASK_OBJECT_FETCH_SIZE))
    }.mapResult { list ->
        if (list.isNotEmpty()) {
            Napier.i(tag = "acg") {
                "topic count ${list.size}"
            }
            acgTask(list.first())
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

private suspend fun Backend.acgTask(topic: Topic): Result<Unit> =
    database.user.getUserAcgByIds(IdListFetch(listOf(topic.author))).map { list ->
        list.associateByPair()
    }.mapResult { userAcgMap ->
        database.user.addAcgForUser(
            listOf(
                TaskRecord(
                    id = SnowflakeFactory.nextId(),
                    createdTime = now(),
                    type = TaskRecordType.TOPIC_ACG,
                    objectId = topic.id,
                ),
            ),
            listOfNotNull(
                userAcgMap[topic.author]?.let { oldAcgAmount ->
                    AssetTransaction(
                        id = SnowflakeFactory.nextId(),
                        uid = topic.author,
                        createdTime = now(),
                        type = AssetType.ACG,
                        before = oldAcgAmount,
                        after = oldAcgAmount + 1,
                    )
                },
            ),
        )
    }

private const val TASK_OBJECT_FETCH_SIZE = 1
