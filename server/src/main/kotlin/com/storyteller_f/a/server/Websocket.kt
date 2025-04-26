package com.storyteller_f.a.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

val messageResponseFlow = MutableSharedFlow<RoomFrame.NewTopicInfo>()
val sharedFlow = messageResponseFlow.asSharedFlow()

suspend fun DefaultWebSocketServerSession.webSocketContent(
    reader: DatabaseReader,
    backend: Backend
) {
    val job = launch {
        sharedFlow.collect { frame ->
            usePrincipalOrNull { uid ->
                sendToMember(backend, frame, uid)
            }
        }
    }

    while (true) {
        try {
            val frame = receiveDeserialized<RoomFrame>()
            usePrincipalOrNull { uid ->
                if (uid != null) {
                    processUserMessage(backend, frame, uid)
                }
            }
        } catch (e: Exception) {
            printWsError(e, reader)
            job.cancel()
            return
        }
    }
}

private suspend fun DefaultWebSocketServerSession.sendToMember(
    backend: Backend,
    frame: RoomFrame.NewTopicInfo,
    uid: PrimaryKey?
) {
    val info = frame.topicInfo
    if (uid != null) {
        call.application.log.info("distribution ${info.id}")
        isMemberJoined(backend, info.rootId, uid).onSuccess { value ->
            if (value && uid != info.author) {
                sendSerialized(frame as RoomFrame)
            }
        }.onFailure { exception ->
            call.application.log.error("distribution ws to $uid", exception)
        }
    }
}

private fun DefaultWebSocketServerSession.printWsError(
    e: Exception,
    reader: DatabaseReader
) {
    when (e) {
        is ClosedReceiveChannelException -> {
            call.application.log.info("ws closed ${call.remoteIp(reader).first()}")
        }

        is CancellationException -> {
            call.application.log.info("ws cancel")
        }

        else -> {
            call.application.log.error("ws receive", e)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.processUserMessage(
    backend: Backend,
    frame: RoomFrame,
    uid: PrimaryKey
) {
    try {
        if (frame is RoomFrame.Message) {
            val newTopic = frame.newTopic
            val content = newTopic.content
            if (content is TopicContent.Plain) {
                if (content.plain.isBlank()) {
                    sendSerialized(RoomFrame.Error("plain is empty") as RoomFrame)
                    return
                }
            } else if (content is TopicContent.Encrypted) {
                if (content.encrypted.isBlank()) {
                    sendSerialized(RoomFrame.Error("message is empty") as RoomFrame)
                    return
                }
            } else {
                sendSerialized(RoomFrame.Error("not support message type") as RoomFrame)
                return
            }
            addTopicAtRoom(backend, newTopic, uid).onSuccess {
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

private suspend fun addTopicAtRoom(
    backend: Backend,
    newTopic: NewRoomTopic,
    uid: PrimaryKey
): Result<TopicInfo?> {
    return when (newTopic.parentType) {
        ObjectType.TOPIC -> {
            DatabaseFactory.getTopicRoot(backend, newTopic.parentId).mapResultIfNotNull { (id, type) ->
                if (type == ObjectType.ROOM) {
                    addTopicIntoRoom(
                        backend,
                        id,
                        uid,
                        newTopic
                    )
                } else {
                    Result.failure(ForbiddenException())
                }
            }
        }

        ObjectType.ROOM -> {
            addTopicIntoRoom(
                backend,
                newTopic.parentId,
                uid,
                newTopic
            )
        }

        else -> {
            Result.failure(ForbiddenException())
        }
    }
}

private suspend fun addTopicIntoRoom(
    backend: Backend,
    roomId: PrimaryKey,
    uid: PrimaryKey,
    newTopic: NewRoomTopic
): Result<TopicInfo?> {
    return isMemberJoined(backend, roomId, uid).mapResult { bool ->
        if (bool) {
            val content = newTopic.content
            val newId = SnowflakeFactory.nextId()
            val topic = Topic(
                newId,
                now(),
                uid,
                roomId,
                ObjectType.ROOM,
                newTopic.parentId,
                newTopic.parentType,
                false,
                null
            )

            checkRoomIsPrivate(backend, roomId).mapResultIfNotNull { isPrivate ->
                if (isPrivate) {
                    when {
                        content !is TopicContent.Encrypted -> Result.failure(
                            ForbiddenException("Private room only accept encrypted content.")
                        )

                        isKeyVerified(
                            backend,
                            roomId,
                            content.encryptedKey
                        ).getOrNull() == true -> DatabaseFactory.saveEncryptedTopic(
                            backend,
                            topic,
                            content
                        )

                        else -> Result.failure(ForbiddenException("Private room only accept encrypted content."))
                    }
                } else {
                    when (content) {
                        is TopicContent.Plain -> DatabaseFactory.savePlainTopic(
                            backend,
                            topic,
                            content = content
                        )

                        else -> Result.failure(ForbiddenException("Public room only accept unencrypted content."))
                    }
                }
            }
        } else {
            Result.failure(ForbiddenException("Can't publish content before join room."))
        }
    }
}

private suspend fun isKeyVerified(
    backend: Backend,
    roomId: PrimaryKey,
    encryptedAes: Map<PrimaryKey, String>
): Result<Boolean> {
    return DatabaseFactory.userListJoinedRoom(backend, roomId).map { value ->
        value.map {
            it.uid
        }.toSet().minus(encryptedAes.keys).isEmpty()
    }
}
