/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now

internal suspend fun Backend.executeTaskObject(
    type: TaskRecordType,
    objectId: PrimaryKey,
    retryRecordId: PrimaryKey? = null,
    failureType: (Throwable) -> String,
    executeAndSaveSuccess: suspend (TaskRecord) -> Unit,
): Result<Unit> {
    val successRecord =
        TaskRecord(
            id = SnowflakeFactory.nextId(),
            createdTime = now(),
            type = type,
            objectId = objectId,
        )
    val result =
        UNIT_RESULT.mapResult {
            executeAndSaveSuccess(successRecord)
            UNIT_RESULT
        }
    result.exceptionOrNull()?.let { failure ->
        database.admin.createTaskRecord(
            TaskRecord(
                id = SnowflakeFactory.nextId(),
                createdTime = now(),
                type = type,
                objectId = objectId,
                failureType = failureType(failure),
                failureReason = failure.message ?: failure::class.simpleName,
            ),
        ).getOrThrow()
    }
    retryRecordId?.let { database.user.updateTaskRecordRetryRequested(it, false).getOrThrow() }
    return result
}

internal suspend fun Backend.getSystemUserId(): PrimaryKey {
    val systemUser = database.user.getRawUser(ObjectFetch.AidFetch(SYSTEM_USER_AID)).getOrThrow()
    return systemUser?.run { user.id } ?: error("System user not found")
}

internal const val DEFAULT_TASK_OBJECT_FETCH_SIZE = 10
private const val SYSTEM_USER_AID = "System"
