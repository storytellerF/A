package com.storyteller_f.a.client.core

import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*

interface WebSocketClient {
    val connectionHandler: LoadingHandler<DefaultClientWebSocketSession>
    val localState: StateFlow<LoadingState?>
    val frameFlow: SharedFlow<RoomFrame>
    suspend fun <T> useWebSocket(block: suspend DefaultClientWebSocketSession.() -> T): Result<T>
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
        coroutineScope {
            val requests = Channel<UserInfo?>(Channel.CONFLATED)
            val connector = launch {
                var connectionJob: Job? = null
                try {
                    for (userInfo in requests) {
                        connectionJob?.cancelAndJoin()
                        connectionJob = null
                        if (userInfo == null) {
                            connectionHandler.data.value?.cancel()
                        } else {
                            connectionJob = launch {
                                while (isActive && canConnectWebSocket(userInfo)) {
                                    connectWebSocketIfNeed(userInfo)
                                    delay(5000)
                                }
                            }
                        }
                    }
                } finally {
                    connectionJob?.cancelAndJoin()
                }
            }
            try {
                combine(sessionModel.state, sessionModel.userHandler.data) { t1, t2 ->
                    t1 to t2
                }.distinctUntilChanged().collect { (state, userInfo) ->
                    if (state is ClientSessionState.Success && userInfo != null) {
                        requests.trySend(userInfo)
                    } else {
                        requests.trySend(null)
                    }
                }
            } finally {
                requests.close()
                connector.cancelAndJoin()
            }
        }
    }

    override suspend fun <T> useWebSocket(block: suspend DefaultClientWebSocketSession.() -> T): Result<T> {
        val old = connectionHandler.data.value
        return if (old != null && old.isActive) {
            localState.value = LoadingState.Loading
            try {
                val r = old.block()
                localState.value = LoadingState.Done
                Result.success(r)
            } catch (e: Exception) {
                localState.value = LoadingState.Error(e)
                Result.failure(e)
            }
        } else {
            Napier.i {
                "useWebSocket failed"
            }
            Result.failure(Exception("WebSocket not connected"))
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

    private fun canConnectWebSocket(userInfo: UserInfo): Boolean {
        return sessionModel.state.value is ClientSessionState.Success &&
                sessionModel.userHandler.data.value == userInfo
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
