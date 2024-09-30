package com.storyteller_f.a.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.service.ForbiddenException
import com.storyteller_f.a.server.service.toTopicInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

val messageResponseFlow = MutableSharedFlow<Pair<RoomFrame, OKey>>()
val sharedFlow = messageResponseFlow.asSharedFlow()

suspend fun DefaultWebSocketServerSession.webSocketContent(backend: Backend) {
    val job = launch {
        sharedFlow.collect { (frame, uid) ->
            call.application.log.info("new frame $frame $uid")
            try {
                if (frame is RoomFrame.Message) {
                    val newTopic = frame.newTopic
                    val newTopicInfo = addTopicAtRoom(newTopic, uid, backend = backend)
                    newTopicInfo.onSuccess {
                        val newFrame: RoomFrame = RoomFrame.NewTopicInfo(it)
                        sendSerialized(newFrame)
                    }.onFailure {
                        val message = it.message ?: "unknown error"
                        val data: RoomFrame = RoomFrame.Error(message)
                        sendSerialized(data)
                    }
                }
            } catch (e: Exception) {
                val newFrame: RoomFrame = RoomFrame.Error(e.message.toString())
                sendSerialized(newFrame)
            }

        }
    }

    while (true) {
        try {
            val frame = receiveDeserialized<RoomFrame>()
            usePrincipalOrNull {
                if (it != null) {
                    messageResponseFlow.emit(frame to it)
                }
            }
        } catch (e: Exception) {
            call.application.log.error("ws receive", e)
            job.cancel()
            return
        }
    }

}

private suspend fun addTopicAtRoom(
    newTopic: NewTopic,
    uid: OKey,
    backend: Backend
): Result<TopicInfo> {
    return when (newTopic.parentType) {
        ObjectType.TOPIC -> {
            val roomInfo = DatabaseFactory.queryNotNull({
                rootId to rootType
            }) {
                Topic.findById(newTopic.parentId)
            }
            if (roomInfo != null && roomInfo.second == ObjectType.ROOM) {
                addTopicIntoRoom(roomInfo, uid, newTopic, backend = backend)
            } else {
                Result.failure(ForbiddenException())
            }
        }

        ObjectType.ROOM -> {
            addTopicIntoRoom(
                newTopic.parentId to newTopic.parentType,
                uid,
                newTopic,
                backend
            )
        }

        else -> {
            Result.failure(ForbiddenException())
        }
    }
}

private suspend fun addTopicIntoRoom(
    roomInfo: Pair<OKey, ObjectType>,
    uid: OKey,
    newTopic: NewTopic,
    backend: Backend
): Result<TopicInfo> {
    val roomId = roomInfo.first
    return if (isRoomJoined(roomId, uid)) {
        val isPrivateChat = DatabaseFactory.dbQuery {
            checkRoomIsPrivate(roomId)
        }
        val content = newTopic.content
        val newId = SnowflakeFactory.nextId()
        val topic = Topic(
            uid,
            roomId,
            ObjectType.ROOM,
            newTopic.parentId,
            newTopic.parentType,
            now(),
            newId,
            now()
        )

        when {
            !isPrivateChat -> {
                if (content is TopicContent.Plain) {
                    Result.success(savePlainTopicContent(topic, content, backend = backend))
                } else {
                    Result.failure(ForbiddenException("Public room only accept unencrypted content."))
                }
            }

            content is TopicContent.Encrypted && isKeyVerified(roomId, content.encryptedKey) -> {
                Result.success(
                    saveEncryptedTopicContent(
                        topic,
                        content.encryptedKey,
                        content.encrypted
                    )
                )
            }

            else -> Result.failure(ForbiddenException("Private room only accept encrypted content."))
        }
    } else {
        Result.failure(ForbiddenException("Can't publish content before join room."))
    }
}

private suspend fun savePlainTopicContent(
    topic: Topic,
    content: TopicContent.Plain,
    backend: Backend
): TopicInfo {
    return DatabaseFactory.dbQuery {
        val newTopicId = Topic.new(topic)
        backend.topicDocumentService.saveDocument(
            listOf(
                TopicDocument(
                    newTopicId,
                    (content).plain
                )
            )
        )
        topic.toTopicInfo()
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun saveEncryptedTopicContent(
    topic: Topic,
    encryptedAes: Map<OKey, String>,
    encryptedContent: String
) = DatabaseFactory.dbQuery {
    val newTopicId = Topic.new(topic)
    EncryptedTopics.insert {
        it[content] = ExposedBlob(encryptedContent.hexToByteArray())
        it[topicId] = newTopicId
    }
    EncryptedTopicKeys.batchInsert(encryptedAes.keys) {
        this[EncryptedTopicKeys.topicId] = newTopicId
        this[EncryptedTopicKeys.uid] = it
        this[EncryptedTopicKeys.encryptedAes] =
            ExposedBlob(encryptedAes[it]!!.hexToByteArray())
    }
    topic.toTopicInfo()
}

private fun isKeyVerified(roomId: OKey, encryptedAes: Map<OKey, String>): Boolean {
    val toSet = RoomJoins.selectAll().where {
        RoomJoins.roomId eq roomId
    }.map {
        RoomJoin.wrapRow(it)
    }.map {
        it.uid
    }.toSet()
    return toSet.minus(encryptedAes.keys).isEmpty()
}

