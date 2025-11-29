package com.storyteller_f.a.cloud.server

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.addUserLog
import com.storyteller_f.a.cloud.core.service.createTopicAtRoom
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.util.logging.error
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

val messageResponseFlow = MutableSharedFlow<RoomFrame.NewTopicInfo>()
val sharedFlow = messageResponseFlow.asSharedFlow()
val userWebSocketSessionMap = mutableMapOf<PrimaryKey, PersistentList<DefaultWebSocketServerSession>>()

suspend fun DefaultWebSocketServerSession.useWebSocket(uid: PrimaryKey, block: suspend () -> Unit) {
    userWebSocketSessionMap[uid] = userWebSocketSessionMap.getOrDefault(uid, persistentListOf()).add(this)
    try {
        block()
    } finally {
        userWebSocketSessionMap[uid] = userWebSocketSessionMap.getOrDefault(uid, persistentListOf()).remove(this)
    }
}

@Serializable
data class Notification(val title: String, val message: String)

interface NotificationDispatcher {
    suspend fun dispatch(frame: RoomFrame.NewTopicInfo): Result<Unit>
}

class WebsocketDispatcher(val session: DefaultWebSocketServerSession) : NotificationDispatcher {
    override suspend fun dispatch(frame: RoomFrame.NewTopicInfo): Result<Unit> {
        return runCatching {
            session.sendFrame(frame)
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

suspend fun DefaultWebSocketServerSession.webSocketContent(
    reader: DatabaseReader,
    backend: Backend,
) {
    usePrincipal { uid ->
        useWebSocket(uid) {
            while (true) {
                try {
                    val frame = receiveDeserialized<RoomFrame>()
                    processUserMessage(backend, frame, uid)
                } catch (e: Exception) {
                    printWsError(e, reader)
                    break
                }
            }
        }
    }
}

private fun DefaultWebSocketServerSession.printWsError(e: Exception, reader: DatabaseReader) {
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
    uid: PrimaryKey,
) {
    call.application.log.info("receive message: $frame")
    try {
        if (frame is RoomFrame.Message) {
            processNewMessage(backend, frame, uid)
        } else {
            rtcChannel.send(RtcFrame(frame, uid, this))
        }
    } catch (e: Exception) {
        call.application.log.error("Catch exception in ws", e)
    }
}

suspend fun DefaultWebSocketServerSession.processNewMessage(
    backend: Backend,
    frame: RoomFrame.Message,
    uid: PrimaryKey,
) {
    val newTopic = frame.newTopic
    val content = newTopic.content
    when (content) {
        is TopicContent.Plain -> {
            if (content.plain.isBlank()) {
                sendFrame(RoomFrame.Error("plain is empty"))
                return
            }
            if (content.plain.length > 1000) {
                sendFrame(RoomFrame.Error("plain is too long"))
            }
        }

        is TopicContent.Encrypted -> {
            if (content.encrypted.isBlank()) {
                sendFrame(RoomFrame.Error("message is empty"))
                return
            }
            if (content.encrypted.length > 1000) {
                sendFrame(RoomFrame.Error("message is too long"))
            }
        }

        else -> {
            sendFrame(RoomFrame.Error("not support message type"))
            return
        }
    }
    backend.createTopicAtRoom(newTopic, uid).onSuccess {
        if (it == null) {
            sendFrame(RoomFrame.Error("not found"))
        } else {
            backend.addUserLog(uid, UserLogType.CREATE, it.tuple())
            val raw = RoomFrame.NewTopicInfo(it)
            sendFrame(raw)
            messageResponseFlow.emit(raw)
        }
    }.onFailure {
        val message = it.message ?: "unknown error"
        call.application.log.error(it)
        sendFrame(RoomFrame.Error(message))
    }
}

private suspend fun dispatchNewMessage(
    backend: Backend,
    httpClient: HttpClient,
): Nothing {
    sharedFlow.collect { frame ->
        backend.database.container.getJoinedUserList(frame.topicInfo.rootId)
            .mapResult { list ->
                val memberJoins = list.filter {
                    it.uid != frame.topicInfo.author
                }
                val dispatchers = memberJoins.mapNotNull {
                    userWebSocketSessionMap[it.uid]
                }.flatten().map {
                    WebsocketDispatcher(it)
                }
                backend.database.user.getUserDevices(memberJoins.map {
                    it.uid
                }).map { list ->
                    list.map {
                        ExternalDispatcher(httpClient, it.endpointUrl)
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

fun Application.startNewMessageTask(backend: Backend) {
    val httpClient = HttpClient {
        expectSuccess = true
        install(Logging)
        install(ContentNegotiation) {
            json()
        }
    }
    val serverJob = launch {
        dispatchNewMessage(backend, httpClient)
    }
    val rtcJob = launch {
        listenerRoomRTC()
    }
    val rtcChannelJob = launch {
        listenerRtcChannel(backend)
    }
    monitor.subscribe(ApplicationStopping) {
        monitor.unsubscribe(ApplicationStopping) {}
        serverJob.cancel()
        httpClient.close()
        rtcChannelJob.cancel()
        rtcJob.cancel()
    }
}
