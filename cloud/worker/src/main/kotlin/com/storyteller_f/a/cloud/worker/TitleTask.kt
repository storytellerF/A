package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.TaskRecord
import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

suspend fun Backend.doTitleTask() {
    database.user.getLatestTaskRecord(TaskRecordType.TITLE).mapResult { taskRecord ->
        val cursor = Cursor.AscCursor(taskRecord?.processedId ?: 0)
        database.title.getAllRawTitles(PrimaryKeyFetch(cursor, 10))
    }.mapResult { result ->
        if (result.list.isEmpty()) {
            Napier.i(tag = "title") {
                "no title to send"
            }
            UNIT_RESULT
        } else {
            Napier.i(tag = "title") {
                "process ${result.list.size} titles"
            }
            runCatching {
                val adminUserResult = database.user.getRawUser(ObjectFetch.AidFetch("System")).getOrThrow()
                val adminAid = adminUserResult?.user?.id ?: throw Exception("System user not found")
                result.list.forEach { rawTitle ->
                    processTitleNotification(rawTitle.title, adminAid)
                }
            }
        }
    }.onSuccess {
        delay(10000)
        Napier.i(tag = "title") {
            "task success $it"
        }
    }.onFailure {
        delay(10000)
        Napier.i(tag = "title", throwable = it) {
            "task failed"
        }
    }
}

private suspend fun Backend.processTitleNotification(title: Title, adminAid: PrimaryKey) {
    val rawUser = database.user.getRawUser(ObjectFetch.IdFetch(title.receiver))
        .getOrThrow() ?: throw Exception("user not found")

    val content = generateTitleNotificationContent(title)
    sendTopicToNotificationRoom(adminAid, rawUser.user, content)

    database.admin.createTaskRecord(
        TaskRecord(SnowflakeFactory.nextId(), now(), TaskRecordType.TITLE, title.id)
    ).getOrThrow()

    Napier.i(tag = "title") {
        "send title notification to user ${title.receiver}"
    }
}

private fun generateTitleNotificationContent(title: Title): String {
    return buildString {
        appendLine("You received a new title!")
        appendLine("Title: ${title.name}")
        appendLine("From: System")
        appendLine("Created: ${title.createdTime}")
        if (title.expiresAt != null) {
            appendLine("Expires: ${title.expiresAt}")
        }
    }
}
