package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.RawUser
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.buildMemberForNotificationRoom
import com.storyteller_f.a.backend.core.types.buildUserNotificationRoom
import com.storyteller_f.a.cloud.core.service.addTopicAtRoom
import com.storyteller_f.shared.buildEncryptedTopicContent
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doIntroTask() {
    database.user.getLatestTaskRecord(TaskRecordType.INTRO).mapResult { taskRecord ->
        val fetch = PrimaryKeyFetch(Cursor.PreCursor(taskRecord?.processedId ?: 1000), 10)
        database.user.getAllUsers(fetch)
    }.mapResult { paginationResult ->
        if (paginationResult.list.isEmpty()) {
            Napier.i(tag = "intro") {
                "no more user, total user count is ${paginationResult.total}"
            }
            Result.success(null)
        } else {
            Napier.i(tag = "intro") {
                "user count ${paginationResult.list.size}"
            }
            sendHelloTopic(paginationResult)
        }
    }.onSuccess {
        delay(10000)
        Napier.i(tag = "intro") {
            "task success $it"
        }
    }.onFailure {
        delay(10000)
        Napier.i(tag = "intro", throwable = it) {
            "task failed"
        }
    }
}

private suspend fun Backend.sendHelloTopic(paginationResult: PaginationResult<RawUser>): Result<Unit> {
    return runCatching {
        val adminAid =
            database.user.getRawUser(ObjectFetch.AidFetch("System")).getOrThrow()!!.user.id
        paginationResult.list.forEach {
            val room = database.room.getRawRoom(ObjectFetch.IdFetch(it.user.notificationId))
                .getOrThrow()?.room ?: database.room.createRoom(
                buildUserNotificationRoom(it.user, adminAid),
                buildMemberForNotificationRoom(it.user, adminAid)
            ).getOrThrow()
            val userPubKeyInfos = database.room.getRoomPubKeyPaginationResult(
                room.id,
                PrimaryKeyFetch(null, 10)
            ).getOrThrow().list
            val encrypted =
                buildEncryptedTopicContent("Hello, ${it.user.nickname}", userPubKeyInfos)
            addTopicAtRoom(
                NewRoomTopic(ObjectType.ROOM, room.id, encrypted),
                adminAid
            ).getOrThrow()
            database.admin.createTaskRecord(
                TaskRecord(
                    SnowflakeFactory.nextId(),
                    now(),
                    TaskRecordType.INTRO,
                    it.user.id
                )
            )
            Napier.i(tag = "intro") {
                "send hello success"
            }
        }
    }
}
