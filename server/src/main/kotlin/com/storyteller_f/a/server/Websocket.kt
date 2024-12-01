package com.storyteller_f.a.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.service.ForbiddenException
import com.storyteller_f.a.server.service.toTopicInfo
import com.storyteller_f.index.TopicDocument
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.error
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

val messageResponseFlow = MutableSharedFlow<RoomFrame.NewTopicInfo>()
val sharedFlow = messageResponseFlow.asSharedFlow()

suspend fun DefaultWebSocketServerSession.webSocketContent(backend: Backend) {
    val job = launch {
        sharedFlow.collect { frame ->
            usePrincipalOrNull { uid ->
                val info = frame.topicInfo
                if (uid != null) {
                    call.application.log.info("distribution ${info.id}")
                    isMemberJoined(info.rootId, uid).onSuccess { value ->
                        if (value && uid != info.author) {
                            sendSerialized(frame as RoomFrame)
                        }
                    }.onFailure { exception ->
                        call.application.log.error("distribution ws to $uid", exception)
                    }
                }
            }

        }
    }

    while (true) {
        try {
            val frame = receiveDeserialized<RoomFrame>()
            usePrincipalOrNull { uid ->
                if (uid != null) {
                    try {
                        if (frame is RoomFrame.Message) {
                            val newTopic = frame.newTopic
                            addTopicAtRoom(newTopic, uid, backend = backend).onSuccess {
                                if (it == null) {
                                    sendSerialized(RoomFrame.Error("not found") as RoomFrame)
                                } else {
                                    val raw = RoomFrame.NewTopicInfo(it)
                                    sendSerialized(raw as RoomFrame)
                                    messageResponseFlow.emit(raw)
                                }
                            }.onFailure {
                                val message = it.message ?: "unknown error"
                                call.application.log.error(it)
                                sendSerialized(RoomFrame.Error(message) as RoomFrame)
                            }
                        }
                    } catch (e: Exception) {
                        call.application.log.error("Catch exception in ws", e)
                    }
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
    uid: PrimaryKey,
    backend: Backend
): Result<TopicInfo?> {
    return when (newTopic.parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.first({
                rootId to rootType
            }, Topic::wrapRow) {
                Topic.findById(newTopic.parentId)
            }.mapResultNotNull { (id, type) ->
                if (type == ObjectType.ROOM) {
                    addTopicIntoRoom(
                        id,
                        uid,
                        newTopic,
                        backend
                    )
                } else {
                    Result.failure(ForbiddenException())
                }
            }
        }

        ObjectType.ROOM -> {
            addTopicIntoRoom(
                newTopic.parentId,
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
    roomId: PrimaryKey,
    uid: PrimaryKey,
    newTopic: NewTopic,
    backend: Backend
): Result<TopicInfo?> {
    return isMemberJoined(roomId, uid).mapResult { bool ->
        if (bool) {
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

            checkRoomIsPrivate(roomId).mapResultNotNull { isPrivate ->
                if (isPrivate) {
                    when {
                        content !is TopicContent.Encrypted -> Result.failure(
                            ForbiddenException("Private room only accept encrypted content.")
                        )

                        isKeyVerified(roomId, content.encryptedKey).getOrNull() == true -> saveEncryptedTopicContent(
                            topic,
                            content.encryptedKey,
                            content.encrypted
                        )

                        else -> Result.failure(ForbiddenException("Private room only accept encrypted content."))
                    }
                } else {
                    when (content) {
                        is TopicContent.Plain -> savePlainTopicContent(topic, content, backend = backend)
                        else -> Result.failure(ForbiddenException("Public room only accept unencrypted content."))
                    }
                }
            }
        } else {
            Result.failure(ForbiddenException("Can't publish content before join room."))
        }
    }
}

private suspend fun savePlainTopicContent(
    topic: Topic,
    content: TopicContent.Plain,
    backend: Backend
): Result<TopicInfo> {
    return DatabaseFactory.dbQuery {
        Topic.new(topic)
        backend.topicDocumentService.saveDocument(
            listOf(TopicDocument.fromTopic(topic, content))
        ).getOrThrow()

        topic.toTopicInfo().copy(content = content)
    }
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun saveEncryptedTopicContent(
    topic: Topic,
    encryptedAes: Map<PrimaryKey, String>,
    encryptedContent: String
) = DatabaseFactory.dbQuery {
    Topic.new(topic)
    EncryptedTopics.insert {
        it[content] = ExposedBlob(encryptedContent.hexToByteArray())
        it[topicId] = topic.id
    }
    EncryptedTopicKeys.batchInsert(encryptedAes.keys) {
        this[EncryptedTopicKeys.topicId] = topic.id
        this[EncryptedTopicKeys.uid] = it
        this[EncryptedTopicKeys.encryptedAes] =
            ExposedBlob(encryptedAes[it]!!.hexToByteArray())
    }
    topic.toTopicInfo().copy(content = TopicContent.Encrypted(encryptedContent, encryptedAes))
}

private suspend fun isKeyVerified(roomId: PrimaryKey, encryptedAes: Map<PrimaryKey, String>): Result<Boolean> {
    return DatabaseFactory.mapQuery({
        MemberJoin.wrapRow(this)
    }) {
        MemberJoins.selectAll().where {
            MemberJoins.objectId eq roomId
        }
    }.map { value ->
        value.map {
            it.uid
        }.toSet().minus(encryptedAes.keys).isEmpty()
    }
}
