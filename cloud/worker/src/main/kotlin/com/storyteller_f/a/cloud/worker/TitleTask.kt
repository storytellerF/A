/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.worker

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storytellerf.a.cloud.worker.DEFAULT_TASK_OBJECT_FETCH_SIZE
import com.storytellerf.a.cloud.worker.executeTaskObject
import com.storytellerf.a.cloud.worker.getSystemUserId
import io.github.aakira.napier.Napier

suspend fun Backend.doTitleTask(fetchSize: Int = DEFAULT_TASK_OBJECT_FETCH_SIZE) {
    val result = executeTitleTask(fetchSize)
    result.fold(
        onSuccess = {
            Napier.i(tag = TITLE_LOG_TAG) {
                "title task completed"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = TITLE_LOG_TAG, throwable = failure) {
                "title task failed"
            }
        },
    )
}

private suspend fun Backend.executeTitleTask(fetchSize: Int): Result<Unit> =
    database.user.getTaskRecordsToRetry(TaskRecordType.TITLE, fetchSize).mapResult { retryRecords ->
        if (retryRecords.isEmpty()) {
            processNextTitles(fetchSize)
        } else {
            retryRecords.forEach { retryRecord ->
                processTitleRetry(retryRecord)
            }
            Result.success(Unit)
        }
    }

private suspend fun Backend.processNextTitles(fetchSize: Int): Result<Unit> =
    database.user.getLatestTaskRecord(TaskRecordType.TITLE).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.objectId ?: 0)
        database.title.getAllRawTitles(PrimaryKeyFetch(cursor, fetchSize))
    }.mapResult { result ->
        if (result.list.isEmpty()) {
            Napier.i(tag = TITLE_LOG_TAG) {
                "no title to send"
            }
        } else {
            Napier.i(tag = TITLE_LOG_TAG) {
                "process ${result.list.size} titles"
            }
            result.list.forEach { rawTitle ->
                processTitle(rawTitle.title, retryRecordId = null)
            }
        }
        Result.success(Unit)
    }

private suspend fun Backend.processTitleRetry(record: TaskRecord) {
    executeTaskObject(
        type = TaskRecordType.TITLE,
        objectId = record.objectId,
        retryRecordId = record.id,
        failureType = { TaskRecordType.DATA_ACCESS_FAILURE },
    ) { successRecord ->
        val title =
            database.title.getTitle(record.objectId).getOrThrow()?.title
                ?: error("Title ${record.objectId} not found")
        processTitleNotification(title, successRecord)
    }.logTitleResult(record.objectId)
}

private suspend fun Backend.processTitle(title: Title, retryRecordId: PrimaryKey?) {
    executeTaskObject(
        type = TaskRecordType.TITLE,
        objectId = title.id,
        retryRecordId = retryRecordId,
        failureType = { TaskRecordType.DATA_ACCESS_FAILURE },
    ) { successRecord ->
        processTitleNotification(title, successRecord)
    }.logTitleResult(title.id)
}

private suspend fun Backend.processTitleNotification(title: Title, successRecord: TaskRecord) {
    val rawUser =
        database.user.getRawUser(ObjectFetch.IdFetch(title.receiver))
            .getOrThrow() ?: throw IllegalStateException("user not found")

    val content = generateTitleNotificationContent(title)
    sendTopicToNotificationRoom(getSystemUserId(), rawUser.user, content).getOrThrow()
    database.admin.createTaskRecord(successRecord).getOrThrow()

    Napier.i(tag = TITLE_LOG_TAG) {
        "send title notification to user ${title.receiver}"
    }
}

private fun Result<Unit>.logTitleResult(objectId: PrimaryKey) {
    fold(
        onSuccess = {
            Napier.i(tag = TITLE_LOG_TAG) {
                "processed title $objectId"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = TITLE_LOG_TAG, throwable = failure) {
                "failed to process title $objectId"
            }
        },
    )
}

private fun generateTitleNotificationContent(title: Title): String =
    buildString {
    appendLine("You received a new title!")
    appendLine("Title: ${title.name}")
    appendLine("From: System")
    appendLine("Created: ${title.createdTime}")
    if (title.expiresAt != null) {
        appendLine("Expires: ${title.expiresAt ?: "<none>"}")
    }
}

private const val TITLE_LOG_TAG = "title"
