package com.storyteller_f.a.server

import com.maxmind.geoip2.DatabaseReader
import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.index.TopicDocument
import com.storyteller_f.a.backend.service.isKeyVerified
import com.storyteller_f.a.backend.service.savePlainTopic
import com.storyteller_f.a.exposed.tables.Topic
import com.storyteller_f.a.server.auth.addUserLog
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.service.checkRootReadPermission
import com.storyteller_f.a.server.service.processTopicExtension
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserLogType
import com.storyteller_f.shared.obj.CustomAnswer
import com.storyteller_f.shared.obj.CustomOffer
import com.storyteller_f.shared.obj.NewRoomTopic
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

suspend fun Backend.sendTopicToRoomMembers() {
    HttpClient {
        expectSuccess = true
        install(Logging)
        install(ContentNegotiation) {
            json()
        }
    }.use { client ->
        sharedFlow.collect { frame ->
            exposedDatabase.containerDatabase.getJoinedUserList(frame.topicInfo.rootId).mapResult { list ->
                val memberJoins = list.filter {
                    it.uid != frame.topicInfo.author
                }
                val dispatchers = memberJoins.mapNotNull {
                    userWebSocketSessionMap[it.uid]
                }.flatten().map {
                    WebsocketDispatcher(it)
                }
                exposedDatabase.userDatabase.getUserDevices(memberJoins.map {
                    it.uid
                }).map { list ->
                    list.map {
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
    backend: Backend,
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
        when (frame) {
            is RoomFrame.Message -> {
                processNewMessage(backend, frame, uid)
            }

            is RoomFrame.SendOffer -> {
                processSendOffer(frame, uid)
            }

            is RoomFrame.SendAnswer -> {
                processSendAnswer(frame, uid)
            }

            is RoomFrame.StartCall -> {
                processStartCall(frame, backend, uid)
            }

            is RoomFrame.StopCall -> {
                processStopCall(frame, uid)
            }

            else -> {}
        }
    } catch (e: Exception) {
        call.application.log.error("Catch exception in ws", e)
    }
}

private suspend fun processStopCall(
    frame: RoomFrame.StopCall,
    uid: PrimaryKey,
) {
    val roomId = frame.roomId
    lock.withLock {
        rtcSession[roomId]?.let {
            it.uidList.remove(uid)
            it.socketMap.remove(uid)
            it.uidSet.remove(uid)
        }
    }
}

private suspend fun DefaultWebSocketServerSession.processStartCall(
    frame: RoomFrame.StartCall,
    backend: Backend,
    uid: PrimaryKey,
) {
    val roomId = frame.roomId
    backend.checkRootReadPermission(ObjectType.ROOM, roomId, uid).onSuccess {
        if (it == null) {
            sendSerialized(RoomFrame.Error("no permission") as RoomFrame)
        } else if (it.hasRead) {
            lock.withLock {
                val list = rtcSession.getOrPut(roomId) {
                    RtcSession(roomId)
                }
                if (!list.uidList.contains(uid)) {
                    list.uidList.add(uid)
                    list.socketMap[uid] = this
                    list.uidSet.add(uid)
                }
            }
        } else {
            sendSerialized(RoomFrame.Error("no permission") as RoomFrame)
        }
    }
}

private suspend fun processSendAnswer(
    frame: RoomFrame.SendAnswer,
    uid: PrimaryKey,
) {
    val answer = frame.answer
    val session = rtcSession[answer.roomId]
    if (session != null) {
        session.socketMap[answer.targetUid]?.sendSerialized(RoomFrame.RespondAnswer(answer))
        session.answerList[uid]?.let {
            it[answer.targetUid] = answer
        }
    }
}

private suspend fun processSendOffer(
    frame: RoomFrame.SendOffer,
    uid: PrimaryKey,
) {
    val offer = frame.offer
    val session = rtcSession[offer.roomId]
    if (session != null) {
        session.socketMap[offer.targetUid]?.sendSerialized(RoomFrame.CreateAnswer(uid, offer))
        session.offerList[uid]?.let {
            it[offer.targetUid] = offer
        }
    }
}

private suspend fun Backend.addTopicAtRoom(
    newTopic: NewRoomTopic,
    uid: PrimaryKey,
): Result<TopicInfo?> {
    return when (newTopic.parentType) {
        ObjectType.TOPIC -> {
            exposedDatabase.topicDatabase.getTopicRootTuple(newTopic.parentId).mapResultIfNotNull { (id, type) ->
                if (type == ObjectType.ROOM) {
                    addTopicIntoRoom(id, uid, newTopic)
                } else {
                    Result.failure(ForbiddenException())
                }
            }
        }

        ObjectType.ROOM -> {
            addTopicIntoRoom(newTopic.parentId, uid, newTopic)
        }

        else -> {
            Result.failure(ForbiddenException())
        }
    }
}

private suspend fun Backend.addTopicIntoRoom(
    roomId: PrimaryKey,
    uid: PrimaryKey,
    newTopic: NewRoomTopic,
): Result<TopicInfo?> {
    val bytes = when (val c = newTopic.content) {
        is TopicContent.Plain -> c.bytes
        is TopicContent.Encrypted -> c.bytes
        else -> throw CustomBadRequestException("unsupported type")
    }
    return exposedDatabase.containerDatabase.isMemberJoined(roomId, uid).mapResult { bool ->
        if (bool) {
            val content = newTopic.content
            val newId = SnowflakeFactory.nextId()
            exposedDatabase.roomData.checkRoomIsPrivate(roomId).mapResultIfNotNull { isPrivate ->
                val topic = Topic(
                    newId,
                    now(),
                    uid,
                    roomId,
                    ObjectType.ROOM,
                    newTopic.parentId,
                    newTopic.parentType,
                    bytes,
                    isPrivate,
                    false,
                    null
                )
                when {
                    isPrivate -> saveEncryptedTopic(content, roomId, topic)
                    content is TopicContent.Plain -> savePlainTopic(topic, content = content).map {
                        topicSearchService.saveDocument(
                            listOf(TopicDocument.Companion.fromTopic(topic, content))
                        ).getOrThrow()
                        it
                    }

                    else -> Result.failure(ForbiddenException("Public room only accept unencrypted content."))
                }
            }.mapResultIfNotNull { topicInfo ->
                processTopicExtension(listOf(topicInfo), uid, false).mapIfNotNull {
                    it.first()
                }
            }
        } else {
            Result.failure(ForbiddenException("Can't publish content before join room."))
        }
    }
}

private suspend fun Backend.saveEncryptedTopic(
    content: TopicContent,
    roomId: PrimaryKey,
    topic: Topic,
): Result<TopicInfo?> = if (content is TopicContent.Encrypted) {
    isKeyVerified(roomId, content.encryptedKey).mapResult {
        if (it) {
            exposedDatabase.topicDatabase.saveEncryptedTopic(topic, content)
        } else {
            Result.failure(ForbiddenException("Key not found ${content.encryptedKey.size}"))
        }
    }
} else {
    Result.failure(ForbiddenException("Private room only accept encrypted content."))
}

data class RtcSession(
    val roomId: PrimaryKey,
    val uidList: MutableList<PrimaryKey> = mutableListOf(),
    val uidSet: MutableSet<PrimaryKey> = mutableSetOf(),
    val socketMap: MutableMap<PrimaryKey, WebSocketServerSession> = mutableMapOf(),
    val offerList: MutableMap<PrimaryKey, MutableMap<PrimaryKey, CustomOffer>> = mutableMapOf(),
    val answerList: MutableMap<PrimaryKey, MutableMap<PrimaryKey, CustomAnswer>> = mutableMapOf(),
)

val rtcSession = mutableMapOf<PrimaryKey, RtcSession>()
val lock = Mutex()

suspend fun Backend.listenerRoomRtc() {
    rtcSession.forEach { (k, it) ->
        it.uidList.reversed().forEachIndexed { i, frontUid ->
            val socketSession = it.socketMap[frontUid]
            if (socketSession != null) {
                if (socketSession.isActive) {
                    it.uidList.forEachIndexed { j, backUid ->
                        if (i > j) {
                            val offer = it.offerList.getOrPut(frontUid) { mutableMapOf() }[backUid]
                            if (offer == null) {
                                try {
                                    socketSession.sendSerialized(RoomFrame.CreateOffer(backUid, k))
                                } catch (e: Exception) {
                                    println("listenerRoomRtc: $e")
                                }
                            }
                        }
                    }
                }
            }
        }
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
