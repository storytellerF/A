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
import io.ktor.server.websocket.sendSerialized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RtcFrame(
    val frame: RoomFrame,
    val uid: PrimaryKey,
    val session: DefaultWebSocketServerSession
)

class RtcUser(val uid: PrimaryKey, val session: DefaultWebSocketServerSession)

data class RtcSession(
    val roomId: PrimaryKey,
    val uidList: MutableList<RtcUser> = mutableListOf(),
    val socketMap: MutableMap<PrimaryKey, DefaultWebSocketServerSession> = mutableMapOf(),
    val offerList: MutableMap<PrimaryKey, MutableMap<PrimaryKey, CustomOffer>> = mutableMapOf(),
    val answerList: MutableMap<PrimaryKey, MutableMap<PrimaryKey, CustomAnswer>> = mutableMapOf(),
)

val rtcSession = mutableMapOf<PrimaryKey, RtcSession>()
val rtcChannel = Channel<RtcFrame> {
}

/**
 * 结束会话
 */
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

/**
 * 发起会话
 */
private suspend fun processStartCall(
    frame: RoomFrame.StartCall,
    backend: Backend,
    uid: PrimaryKey,
    session: DefaultWebSocketServerSession,
) {
    Napier.i {
        "processStartCall $uid"
    }

    val roomId = frame.roomId
    backend.checkRootReadPermission(ObjectType.ROOM, roomId, uid).onSuccess { permission ->
        if (permission == null) {
            session.sendFrame(RoomFrame.Error("no permission"))
        } else {
            val list = rtcSession.getOrPut(roomId) {
                RtcSession(roomId)
            }
            if (list.uidList.size < 2 && list.uidList.firstOrNull {
                    it.uid == uid
                } == null) {
                list.uidList.add(RtcUser(uid, session))
                list.socketMap[uid] = session
            }
        }
    }.onFailure {
        session.sendFrame(RoomFrame.Error(it.message.toString()))
    }
}

/**
 * answer 创建成功，要求另一个用户回应answer
 */
private suspend fun processSendAnswer(
    frame: RoomFrame.SendAnswer,
    uid: PrimaryKey,
) {
    val answer = frame.answer
    val session = rtcSession[frame.roomId]
    if (session != null) {
        session.socketMap[frame.targetUid]?.sendFrame(
            RoomFrame.RespondAnswer(
                answer,
                frame.roomId,
                frame.targetUid
            )
        )
        session.answerList[uid]?.let {
            it[frame.targetUid] = answer
        }
    }
}

/**
 * offer 创建成功，要求另一个用户创建answer
 */
private suspend fun processSendOffer(
    frame: RoomFrame.SendOffer,
    uid: PrimaryKey,
) {
    val offer = frame.offer
    val session = rtcSession[frame.roomId]
    Napier.i {
        "processSendOffer $frame ${session?.socketMap[frame.targetUid]}"
    }
    if (session != null) {
        session.socketMap[frame.targetUid]?.sendFrame(
            RoomFrame.CreateAnswer(
                uid,
                offer,
                frame.roomId
            )
        )
        session.offerList[uid]?.let {
            it[frame.targetUid] = offer
        }
    }
}

suspend fun listenerRoomRTC() {
    while (true) {
        rtcSession.forEach { (roomId, it) ->
            it.uidList.forEachIndexed { frontUserIndex, frontRtcUser ->
                val frontSocket = frontRtcUser.session
                if (!(frontSocket.isActive)) return@forEachIndexed
                it.uidList.forEachIndexed { backUserIndex, backRtcUser ->
                    processRTCSession(
                        frontUserIndex,
                        backUserIndex,
                        backRtcUser,
                        it,
                        frontRtcUser,
                        frontSocket,
                        roomId
                    )
                }
            }
        }
        withContext(Dispatchers.IO) {
            delay(1000)
        }
    }
}

private suspend fun processRTCSession(
    frontUserIndex: Int,
    backUserIndex: Int,
    backRtcUser: RtcUser,
    session: RtcSession,
    frontRtcUser: RtcUser,
    frontSocket: DefaultWebSocketServerSession,
    roomId: PrimaryKey
) {
    if (frontUserIndex >= backUserIndex) return
    val backSocket = backRtcUser.session
    if (!backSocket.isActive) return
    val offer = session.offerList.getOrPut(frontRtcUser.uid) { mutableMapOf() }[backRtcUser.uid]
    Napier.i {
        "processRTCSession $frontUserIndex ${frontRtcUser.uid} $backUserIndex ${backRtcUser.uid} $offer"
    }
    if (offer != null) {
        val answer =
            session.answerList.getOrPut(frontRtcUser.uid) { mutableMapOf() }[backRtcUser.uid]
        if (answer != null) return
        try {
            val frame = RoomFrame.CreateAnswer(frontRtcUser.uid, offer, session.roomId)
            backSocket.sendFrame(frame)
        } catch (e: Exception) {
            Napier.e(e) {
                "send CreateAnswer"
            }
        }
        return
    }
    try {
        val frame = RoomFrame.CreateOffer(backRtcUser.uid, roomId)
        frontSocket.sendFrame(frame)
    } catch (e: Exception) {
        Napier.e(e) {
            "send CreateOffer"
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
                    processStartCall(frame, backend, uid, rtcFrame.session)
                }

                is RoomFrame.StopCall -> {
                    processStopCall(frame, uid)
                }

                is RoomFrame.SendCandidate -> {
                    processSendCandidate(frame, uid)
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

suspend fun processSendCandidate(
    frame: RoomFrame.SendCandidate,
    uid: PrimaryKey
) {
    val session = rtcSession[frame.roomId] ?: return
    val targetSession = session.socketMap[frame.targetUid] ?: return
    val f = RoomFrame.ReceiveCandidate(
        frame.candidate,
        frame.roomId,
        uid
    )
    targetSession.sendFrame(f)
}

suspend fun DefaultWebSocketServerSession.sendFrame(frame: RoomFrame) {
    sendSerialized(frame)
}
