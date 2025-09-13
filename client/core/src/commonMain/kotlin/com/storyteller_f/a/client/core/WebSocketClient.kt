package com.storyteller_f.a.client.core

import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.CustomAnswer
import com.storyteller_f.shared.obj.CustomOffer
import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*

interface WebSocketClientListener {
    suspend fun onReceived(frame: RoomFrame)
}

interface WebSocketClient {
    val connectionHandler: LoadingHandler<DefaultClientWebSocketSession>
    val localState: StateFlow<LoadingState?>
    val remoteState: SharedFlow<RoomFrame>
    fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job?
    fun addListener(listener: WebSocketClientListener)
    fun removeListener(listener: WebSocketClientListener)
}

@OptIn(DelicateCoroutinesApi::class)
class WebSocketClientImpl(
    val sessionModel: SessionModel,
    val buildConnection: suspend (UserInfo, String) -> DefaultClientWebSocketSession,
    val onMessage: suspend (RoomFrame) -> Unit,
) : WebSocketClient {
    override val connectionHandler = FixedLoadingHandler<DefaultClientWebSocketSession>()
    override val localState = MutableStateFlow<LoadingState?>(null)
    override val remoteState = MutableSharedFlow<RoomFrame>()
    private val listeners = mutableListOf<WebSocketClientListener>()

    suspend fun connectWebSocket() {
        combine(sessionModel.state, sessionModel.userHandler.data) { t1, t2 ->
            t1 to t2
        }.distinctUntilChanged().collect { (state, userInfo) ->
            if (state is ClientSessionState.Success && userInfo != null) {
                while (true) {
                    connectWebSocketIfNeed(userInfo)
                    delay(5000)
                }
            } else {
                connectionHandler.data.value?.cancel()
            }
        }
    }

    override fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job? {
        val old = connectionHandler.data.value
        return if (old != null && old.isActive) {
            old.launch {
                localState.value = LoadingState.Loading
                localState.value = try {
                    old.block()
                    LoadingState.Done
                } catch (e: Exception) {
                    LoadingState.Error(e)
                }
            }
        } else {
            Napier.i {
                "useWebSocket failed"
            }
            null
        }
    }

    private suspend fun connectWebSocketIfNeed(userInfo: UserInfo) {
        val (_, sig) = sessionModel.dataAndSignature ?: return
        if (sig == null) return
        when (connectionHandler.state.value) {
            is LoadingState.Loading -> return
            is LoadingState.Done -> {
                val oldSession = connectionHandler.data.value
                if (oldSession?.isActive == true) return
            }

            else -> {}
        }
        connectionHandler.state.markLoading()
        try {
            val session = buildConnection(userInfo, sig)
            startListenerWebSocket(session, userInfo)
            connectionHandler.done(session)
            connectionHandler.state.markDone()
        } catch (e: Exception) {
            connectionHandler.state.markError(e)
        }
    }

    private fun startListenerWebSocket(session: DefaultClientWebSocketSession, userInfo: UserInfo) {
        session.launch {
            while (isActive) {
                try {
                    val frame = session.receiveDeserialized<RoomFrame>()
                    Napier.i(tag = "ClientWebSocket") {
                        "client ${userInfo.id} receive $frame"
                    }
                    onMessage(frame)
                    when (frame) {
                        is RoomFrame.Error -> {
                            remoteState.emit(frame)
                        }

                        is RoomFrame.NewTopicInfo -> {
                            listeners.forEach {
                                it.onReceived(frame)
                            }
                        }

                        is RoomFrame.CreateOffer -> {
                            session.sendFrame(
                                RoomFrame.SendOffer(
                                    CustomOffer(
                                        "offer",
                                        frame.roomId,
                                        frame.targetUid
                                    )
                                )
                            )
                        }

                        is RoomFrame.CreateAnswer -> {
                            session.sendFrame(
                                RoomFrame.SendAnswer(
                                    CustomAnswer(
                                        "answer",
                                        frame.offer.roomId,
                                        frame.targetUid
                                    )
                                )
                            )
                        }

                        else -> {}
                    }
                } catch (e: Exception) {
                    if (e is ClosedReceiveChannelException) {
                        Napier.e {
                            "Listener WebSocket failed: ${e.message}"
                        }
                    } else {
                        Napier.e(e, tag = "ClientWebSocket") {
                            "Listener WebSocket failed"
                        }
                    }
                    break
                }
            }
            Napier.i {
                "web socket unactive"
            }
            connectionHandler.data.value = null
            connectionHandler.state.value = null
        }
    }

    override fun addListener(listener: WebSocketClientListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: WebSocketClientListener) {
        listeners.remove(listener)
    }
}

suspend fun DefaultClientWebSocketSession.sendFrame(roomFrame: RoomFrame) {
    sendSerialized(roomFrame)
}