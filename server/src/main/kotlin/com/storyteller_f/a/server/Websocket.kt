package com.storyteller_f.a.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.ForbiddenException
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.service.processTopicExtension
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.*
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

val messageResponseFlow = MutableSharedFlow<RoomFrame.NewTopicInfo>()
val sharedFlow = messageResponseFlow.asSharedFlow()
val userWebSocketSessionMap = mutableMapOf<PrimaryKey, List<DefaultWebSocketServerSession>>()

@Serializable
data class Notification(val title: String, val message: String)

interface NotificationDispatcher {
    suspend fun dispatch(frame: RoomFrame.NewTopicInfo): Result<Unit>
}

class WebsocketDispatcher(val session: DefaultWebSocketServerSession) : NotificationDispatcher {
    override suspend fun dispatch(frame: RoomFrame.NewTopicInfo): Result<Unit> {
        return runCatching {
            session.sendSerialized<RoomFrame>(frame)
        }
    }
}

class ExternalDispatcher(val client: HttpClient, val endpointUrl: String) : NotificationDispatcher {
    override suspend fun dispatch(frame: RoomFrame.NewTopicInfo): Result<Unit> {
        return runCatching {
            val content = when (val content = frame.topicInfo.content) {
                is TopicContent.Plain -> content.plain
                else -> ""
            }
            client.post(endpointUrl) {
                contentType(ContentType.Application.Json)
                setBody(Notification("new topic", content))
            }
        }
    }
}

suspend fun sendTopicToRoomMembers(backend: Backend) {
    HttpClient {
        expectSuccess = true
        install(Logging)
        install(ContentNegotiation) {
            json()
        }
    }.use { client ->
        sharedFlow.collect { frame ->
            DatabaseFactory.getJoinedUserList(backend, frame.topicInfo.rootId).mapResult { list ->
                val memberJoins = list.filter {
                    it.uid != frame.topicInfo.author
                }
                val dispatchers = memberJoins.mapNotNull {
                    userWebSocketSessionMap[it.uid]
                }.flatMap {
                    it.map {
                        WebsocketDispatcher(it)
                    }
                }
                DatabaseFactory.getUserDevices(backend, memberJoins.map {
                    it.uid
                }).map {
                    it.map {
                        ExternalDispatcher(client, it.endpointUrl)
                    } + dispatchers
                }
            }.onSuccess {
                it.forEach { dispatcher ->
                    dispatcher.dispatch(frame).onFailure { throwable ->
                        Napier.e(throwable = throwable) {
                            "send topic to room members failed: ${throwable.message}"
                        }
                    }
                }
            }.onFailure {
                Napier.e(throwable = it) {
                    "send topic to room members failed: ${it.message}"
                }
            }
        }
    }
}

suspend fun DefaultWebSocketServerSession.webSocketContent(
    reader: DatabaseReader,
    backend: Backend
) {
    usePrincipalOrNull { uid ->
        if (uid == null) {
            return@usePrincipalOrNull
        }
        userWebSocketSessionMap[uid] = userWebSocketSessionMap.getOrDefault(uid, emptyList()) + this
        while (true) {
            try {
                val frame = receiveDeserialized<RoomFrame>()
                processUserMessage(backend, frame, uid)
            } catch (e: Exception) {
                printWsError(e, reader)
                userWebSocketSessionMap[uid] = userWebSocketSessionMap.getOrDefault(uid, emptyList()) - this
                return
            }
        }
    }
}

private fun DefaultWebSocketServerSession.printWsError(
    e: Exception,
    reader: DatabaseReader
) {
    val log = call.application.log
    when (e) {
        is ClosedReceiveChannelException -> log.info("ws closed ${call.remoteIp(reader).first()}")

        is CancellationException -> log.info("ws cancel")

        else -> log.error("ws receive", e)
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
            when (content) {
                is TopicContent.Plain -> {
                    if (content.plain.isBlank()) {
                        sendSerialized(RoomFrame.Error("plain is empty") as RoomFrame)
                        return
                    }
                }

                is TopicContent.Encrypted -> {
                    if (content.encrypted.isBlank()) {
                        sendSerialized(RoomFrame.Error("message is empty") as RoomFrame)
                        return
                    }
                }

                else -> {
                    sendSerialized(RoomFrame.Error("not support message type") as RoomFrame)
                    return
                }
            }
            addTopicAtRoom(backend, newTopic, uid).onSuccess {
                if (it == null) {
                    sendSerialized(RoomFrame.Error("not found") as RoomFrame)
                } else {
                    addUserLog(backend, uid, UserLogType.CREATE, it.tuple())
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
                    if (content is TopicContent.Encrypted) {
                        isKeyVerified(
                            backend,
                            roomId,
                            content.encryptedKey
                        ).mapResult {
                            if (it) {
                                DatabaseFactory.saveEncryptedTopic(
                                    backend,
                                    topic,
                                    content
                                )
                            } else {
                                Result.failure(ForbiddenException("Key not found ${content.encryptedKey.size}"))
                            }
                        }
                    } else {
                        Result.failure(
                            ForbiddenException("Private room only accept encrypted content.")
                        )
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
            }.mapResultIfNotNull { topicInfo ->
                processTopicExtension(backend, listOf(topicInfo), uid, topicInfo.isPrivate, false).mapIfNotNull {
                    it.first()
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
    return DatabaseFactory.getJoinedUserList(backend, roomId).map { value ->
        value.map {
            it.uid
        }.toSet().minus(encryptedAes.keys).isEmpty()
    }
}
