package com.storyteller_f.a.client.core

import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*

interface WebSocketClient {
    val connectionHandler: LoadingHandler<DefaultClientWebSocketSession>
    val localState: StateFlow<LoadingState?>
    val frameFlow: SharedFlow<RoomFrame>
    fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job?
}

@OptIn(DelicateCoroutinesApi::class)
class WebSocketClientImpl(
    val sessionModel: UserSessionModel,
    val buildConnection: suspend (UserInfo, String) -> DefaultClientWebSocketSession,
    val onMessage: suspend (RoomFrame, DefaultClientWebSocketSession) -> Unit,
) : WebSocketClient {
    override val connectionHandler = FixedLoadingHandler<DefaultClientWebSocketSession>()
    override val localState = MutableStateFlow<LoadingState?>(null)
    override val frameFlow = MutableSharedFlow<RoomFrame>()

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
        Napier.i(tag = "ws") {
            "loading"
        }
        try {
            val session = buildConnection(userInfo, sig)
            startListenerWebSocket(session, userInfo)
            connectionHandler.done(session)
            connectionHandler.state.markDone()
            Napier.i(tag = "ws") {
                "done"
            }
        } catch (e: Exception) {
            connectionHandler.state.markError(e)
            Napier.i(tag = "ws") {
                "error"
            }
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
                    onMessage(frame, session)
                    frameFlow.emit(frame)
                } catch (e: Exception) {
                    if (e is ClosedReceiveChannelException) {
                        Napier.e {
                            "WebSocket closed ${userInfo.id}"
                        }
                    } else {
                        Napier.e(e, tag = "ClientWebSocket") {
                            "WebSocket failed: ${e.message} ${userInfo.id}"
                        }
                    }
                    break
                }
            }
            Napier.i {
                "Client WebSocket disconnected ${userInfo.id}"
            }
            connectionHandler.data.value = null
            connectionHandler.state.value = null
        }
    }
}

suspend fun DefaultClientWebSocketSession.sendFrame(roomFrame: RoomFrame) {
    sendSerialized(roomFrame)
}
