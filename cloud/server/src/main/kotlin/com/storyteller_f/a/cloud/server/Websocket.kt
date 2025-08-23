package com.storyteller_f.a.cloud.server

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.core.service.addTopicAtRoom
import com.storyteller_f.a.cloud.core.service.addUserLog
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
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
import kotlinx.coroutines.launch
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

suspend fun DefaultWebSocketServerSession.webSocketContent(
    reader: DatabaseReader,
    backend: Backend,
) {
    usePrincipal { uid ->
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
    reader: DatabaseReader,
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
    uid: PrimaryKey,
) {
    try {
        if (frame is RoomFrame.Message) {
            processNewMessage(backend, frame, uid)
        } else {
            rtcChannel.send(RtcFrame(frame, uid))
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
                sendSerialized(RoomFrame.Error("plain is empty") as RoomFrame)
                return
            }
            if (content.plain.length > 1000) {
                sendSerialized(RoomFrame.Error("plain is too long") as RoomFrame)
            }
        }

        is TopicContent.Encrypted -> {
            if (content.encrypted.isBlank()) {
                sendSerialized(RoomFrame.Error("message is empty") as RoomFrame)
                return
            }
            if (content.encrypted.length > 1000) {
                sendSerialized(RoomFrame.Error("message is too long") as RoomFrame)
            }
        }

        else -> {
            sendSerialized(RoomFrame.Error("not support message type") as RoomFrame)
            return
        }
    }
    backend.addTopicAtRoom(newTopic, uid).onSuccess {
        if (it == null) {
            sendSerialized(RoomFrame.Error("not found") as RoomFrame)
        } else {
            backend.addUserLog(uid, UserLogType.CREATE, it.tuple())
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

private suspend fun dispatchNewMessage(
    backend: Backend,
    httpClient: HttpClient,
): Nothing {
    sharedFlow.collect { frame ->
        backend.combinedDatabase.containerDatabase.getJoinedUserList(frame.topicInfo.rootId).mapResult { list ->
            val memberJoins = list.filter {
                it.uid != frame.topicInfo.author
            }
            val dispatchers = memberJoins.mapNotNull {
                userWebSocketSessionMap[it.uid]
            }.flatten().map {
                WebsocketDispatcher(it)
            }
            backend.combinedDatabase.userDatabase.getUserDevices(memberJoins.map {
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
        listenerRoomRtc()
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
