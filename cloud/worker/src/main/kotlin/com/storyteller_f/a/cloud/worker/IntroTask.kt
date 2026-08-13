package com.storyteller_f.a.cloud.worker

import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.Room
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.a.backend.core.types.buildMemberForNotificationRoom
import com.storyteller_f.a.backend.core.types.buildUserNotificationRoom
import com.storyteller_f.a.cloud.core.service.createTopicAtRoom
import com.storyteller_f.a.cloud.ws.api.GlobalWsEventPublisher
import com.storyteller_f.shared.buildEncryptedTopicContent
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storytellerf.a.cloud.worker.DEFAULT_TASK_OBJECT_FETCH_SIZE
import com.storytellerf.a.cloud.worker.executeTaskObject
import com.storytellerf.a.cloud.worker.getSystemUserId
import io.github.aakira.napier.Napier

suspend fun Backend.doIntroTask(fetchSize: Int = DEFAULT_TASK_OBJECT_FETCH_SIZE) {
    val result = executeIntroTask(fetchSize)
    result.fold(
        onSuccess = {
            Napier.i(tag = INTRO_LOG_TAG) {
                "intro task completed"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = INTRO_LOG_TAG, throwable = failure) {
                "intro task failed"
            }
        },
    )
}

private suspend fun Backend.executeIntroTask(fetchSize: Int): Result<Unit> =
    database.user.getTaskRecordsToRetry(TaskRecordType.INTRO, fetchSize).mapResult { retryRecords ->
        if (retryRecords.isEmpty()) {
            processNextIntroUsers(fetchSize)
        } else {
            retryRecords.forEach { retryRecord ->
                processIntroRetry(retryRecord)
            }
            Result.success(Unit)
        }
    }

private suspend fun Backend.processNextIntroUsers(fetchSize: Int): Result<Unit> =
    database.user.getLatestTaskRecord(TaskRecordType.INTRO).mapResult { taskRecord ->
        val fetch =
            PrimaryKeyFetch(
                Cursor.AscCursor(taskRecord?.objectId ?: INTRO_START_OBJECT_ID),
                fetchSize,
            )
        database.user.getAllUsers(fetch)
    }.mapResult { paginationResult ->
        if (paginationResult.list.isEmpty()) {
            Napier.i(tag = INTRO_LOG_TAG) {
                "no more user, total user count is ${paginationResult.total}"
            }
        } else {
            Napier.i(tag = INTRO_LOG_TAG) {
                "process ${paginationResult.list.size} users"
            }
            paginationResult.list.forEach { rawUser ->
                processIntroUser(rawUser, retryRecordId = null)
            }
        }
        Result.success(Unit)
    }

private suspend fun Backend.processIntroRetry(record: TaskRecord) {
    executeTaskObject(
        type = TaskRecordType.INTRO,
        objectId = record.objectId,
        retryRecordId = record.id,
        failureType = { TaskRecordType.DATA_ACCESS_FAILURE },
    ) { successRecord ->
        val rawUser =
            database.user.getRawUser(ObjectFetch.IdFetch(record.objectId)).getOrThrow()
                ?: error("User ${record.objectId} not found")
        sendHelloTopic(rawUser, successRecord)
    }.logIntroResult(record.objectId)
}

private suspend fun Backend.processIntroUser(rawUser: RawUser, retryRecordId: PrimaryKey?) {
    executeTaskObject(
        type = TaskRecordType.INTRO,
        objectId = rawUser.user.id,
        retryRecordId = retryRecordId,
        failureType = { TaskRecordType.DATA_ACCESS_FAILURE },
    ) { successRecord ->
        sendHelloTopic(rawUser, successRecord)
    }.logIntroResult(rawUser.user.id)
}

private suspend fun Backend.sendHelloTopic(rawUser: RawUser, successRecord: TaskRecord) {
    val systemUserId = getSystemUserId()
    sendTopicToNotificationRoom(systemUserId, rawUser.user, "Hello, ${rawUser.user.nickname}")
    database.admin.createTaskRecord(successRecord).getOrThrow()
}

private fun Result<Unit>.logIntroResult(objectId: PrimaryKey) {
    fold(
        onSuccess = {
            Napier.i(tag = INTRO_LOG_TAG) {
                "sent hello topic to user $objectId"
            }
        },
        onFailure = { failure ->
            Napier.e(tag = INTRO_LOG_TAG, throwable = failure) {
                "failed to send hello topic to user $objectId"
            }
        },
    )
}

suspend fun Backend.sendTopicToNotificationRoom(uid: PrimaryKey, user: User, content: String) {
    val room = getNotificationRoom(user) ?: createNotificationRoom(user, uid)
    sedTopicAtRoom(uid, room.id, content)
}

private suspend fun Backend.getNotificationRoom(user: User): Room? =
    database.room.getRawRoom(ObjectFetch.IdFetch(user.notificationId)).getOrThrow()?.room

private suspend fun Backend.createNotificationRoom(user: User, uid: PrimaryKey): Room {
    val room =
        database.room.createRoom(
            buildUserNotificationRoom(user, uid),
            buildMemberForNotificationRoom(user, uid),
        ).getOrThrow()
    return room
}

suspend fun Backend.sedTopicAtRoom(uid: PrimaryKey, roomId: PrimaryKey, content: String) {
    val userPubKeyInfos =
        database.room.getRoomPubKeyPaginationResult(
            roomId,
            PrimaryKeyFetch(null, 10),
        ).getOrThrow().list
    val encrypted = buildEncryptedTopicContent(content, userPubKeyInfos)
    createTopicAtRoom(NewRoomTopic(ObjectType.ROOM, roomId, encrypted), uid).getOrThrow()?.let {
        GlobalWsEventPublisher.publishNewTopic(RoomFrame.NewTopicInfo(it))
    }
}

private const val INTRO_START_OBJECT_ID = 1000L
private const val INTRO_LOG_TAG = "intro"
