package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
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
import com.storyteller_f.shared.buildEncryptedTopicContent
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doIntroTask() {
    database.user.getLatestTaskRecord(TaskRecordType.INTRO).mapResult { taskRecord ->
        val fetch = PrimaryKeyFetch(Cursor.AscCursor(taskRecord?.processedId ?: 1000), 10)
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
            sendHelloTopic(paginationResult.list)
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

private suspend fun Backend.sendHelloTopic(rawUsers: List<RawUser>): Result<Unit> {
    return runCatching {
        val adminAid = database.user.getRawUser(ObjectFetch.AidFetch("System")).getOrThrow()!!.user.id
        rawUsers.forEach {
            sendTopicToNotificationRoom(adminAid, it.user, "Hello, ${it.user.nickname}")
            database.admin.createTaskRecord(
                TaskRecord(SnowflakeFactory.nextId(), now(), TaskRecordType.INTRO, it.user.id)
            )
            Napier.i(tag = "intro") {
                "send hello success"
            }
        }
    }
}

suspend fun Backend.sendTopicToNotificationRoom(uid: PrimaryKey, user: User, content: String) {
    val room = getNotificationRoom(user) ?: createNotificationRoom(user, uid)
    sedTopicAtRoom(uid, room.id, content)
}

private suspend fun Backend.getNotificationRoom(user: User): Room? =
    database.room.getRawRoom(ObjectFetch.IdFetch(user.notificationId)).getOrThrow()?.room

private suspend fun Backend.createNotificationRoom(user: User, uid: PrimaryKey): Room =
    database.room.createRoom(
        buildUserNotificationRoom(user, uid),
        buildMemberForNotificationRoom(user, uid)
    ).getOrThrow()

suspend fun Backend.sedTopicAtRoom(
    uid: PrimaryKey,
    roomId: PrimaryKey,
    content: String
) {
    val userPubKeyInfos = database.room.getRoomPubKeyPaginationResult(
        roomId,
        PrimaryKeyFetch(null, 10)
    ).getOrThrow().list
    val encrypted = buildEncryptedTopicContent(content, userPubKeyInfos)
    createTopicAtRoom(NewRoomTopic(ObjectType.ROOM, roomId, encrypted), uid).getOrThrow()
}
