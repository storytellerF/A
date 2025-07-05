package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.core.service.checkRootReadPermission
import com.storyteller_f.shared.obj.CustomAnswer
import com.storyteller_f.shared.obj.CustomOffer
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RtcFrame(val frame: RoomFrame, val uid: PrimaryKey)

class RtcUser(val uid: PrimaryKey, val session: DefaultWebSocketServerSession)

data class RtcSession(
    val roomId: PrimaryKey,
    val uidList: MutableList<RtcUser> = mutableListOf(),
    val socketMap: MutableMap<PrimaryKey, WebSocketServerSession> = mutableMapOf(),
    val offerList: MutableMap<PrimaryKey, MutableMap<PrimaryKey, CustomOffer>> = mutableMapOf(),
    val answerList: MutableMap<PrimaryKey, MutableMap<PrimaryKey, CustomAnswer>> = mutableMapOf(),
)

val rtcSession = mutableMapOf<PrimaryKey, RtcSession>()
val rtcChannel = Channel<RtcFrame> {
}

private fun processStopCall(
    frame: RoomFrame.StopCall,
    uid: PrimaryKey,
) {
    val roomId = frame.roomId
    val session = rtcSession[roomId] ?: return
    session.uidList.removeIf {
        it.uid == uid
    }
    session.socketMap.remove(uid)
    if (session.uidList.isEmpty()) {
        rtcSession.remove(roomId)
    }
}

private suspend fun processStartCall(
    frame: RoomFrame.StartCall,
    backend: Backend,
    uid: PrimaryKey,
) {
    val session = userWebSocketSessionMap[uid]?.firstOrNull() ?: return
    val roomId = frame.roomId
    backend.checkRootReadPermission(ObjectType.ROOM, roomId, uid).onSuccess { permission ->
        if (permission == null) {
            session.sendSerialized(RoomFrame.Error("no permission") as RoomFrame)
        } else if (permission.hasRead) {
            val list = rtcSession.getOrPut(roomId) {
                RtcSession(roomId)
            }
            if (list.uidList.firstOrNull {
                    it.uid == uid
                } == null) {
                list.uidList.add(RtcUser(uid, session))
                list.socketMap[uid] = session
            }
        } else {
            session.sendSerialized(RoomFrame.Error("no permission") as RoomFrame)
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
        session.socketMap[answer.targetUid]?.sendSerialized(RoomFrame.RespondAnswer(answer) as RoomFrame)
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
        session.socketMap[offer.targetUid]?.sendSerialized(RoomFrame.CreateAnswer(uid, offer) as RoomFrame)
        session.offerList[uid]?.let {
            it[offer.targetUid] = offer
        }
    }
}

suspend fun listenerRoomRtc() {
    while (true) {
        rtcSession.forEach { (roomId, it) ->
            it.uidList.forEachIndexed { i, frontRtcUser ->
                val frontSocket = frontRtcUser.session
                if (frontSocket.isActive) {
                    it.uidList.forEachIndexed { j, backRtcUser ->
                        if (i < j) {
                            val backSocket = backRtcUser.session
                            if (backSocket.isActive) {
                                val offer = it.offerList.getOrPut(frontRtcUser.uid) { mutableMapOf() }[backRtcUser.uid]
                                Napier.i {
                                    "listenerRoomRtc $i ${frontRtcUser.uid} $j ${backRtcUser.uid} $offer"
                                }
                                if (offer == null) {
                                    try {
                                        frontSocket.sendSerialized(
                                            RoomFrame.CreateOffer(
                                                backRtcUser.uid,
                                                roomId
                                            ) as RoomFrame
                                        )
                                    } catch (e: Exception) {
                                        Napier.e(e) {
                                            "send CreateOffer"
                                        }
                                    }
                                } else {
                                    val answer =
                                        it.answerList.getOrPut(frontRtcUser.uid) { mutableMapOf() }[backRtcUser.uid]
                                    if (answer == null) {
                                        try {
                                            backSocket.sendSerialized(
                                                RoomFrame.CreateAnswer(
                                                    frontRtcUser.uid,
                                                    offer
                                                ) as RoomFrame
                                            )
                                        } catch (e: Exception) {
                                            Napier.e(e) {
                                                "send CreateAnswer"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.IO) {
            delay(1000)
        }
    }
}

suspend fun listenerRtcChannel(backend: Backend) {
    for (rtcFrame in rtcChannel) {
        val frame = rtcFrame.frame
        val uid = rtcFrame.uid
        Napier.d(tag = "rtc") {
            "receive $frame $uid"
        }
        try {
            when (frame) {
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

                else -> {
                }
            }
        } catch (e: Throwable) {
            Napier.e(e) {
                "catch exception"
            }
        }
    }
}
