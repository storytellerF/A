package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import java.net.SocketTimeoutException

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

    companion object {
        val EMPTY = object : WebSocketClient {
            override val connectionHandler: LoadingHandler<DefaultClientWebSocketSession>
                get() = TODO("Not yet implemented")
            override val localState: StateFlow<LoadingState?>
                get() = TODO("Not yet implemented")
            override val remoteState: SharedFlow<RoomFrame>
                get() = TODO("Not yet implemented")

            override fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job? {
                TODO("Not yet implemented")
            }

            override fun addListener(listener: WebSocketClientListener) {
                TODO("Not yet implemented")
            }

            override fun removeListener(listener: WebSocketClientListener) {
                TODO("Not yet implemented")
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
class WebSocketClientImpl(
    val sessionModel: SessionModel,
    val buildConnection: suspend (UserInfo, String) -> DefaultClientWebSocketSession,
    val onMessage: suspend (RoomFrame) -> Unit
) : WebSocketClient {
    override val connectionHandler = FixedLoadingHandler<DefaultClientWebSocketSession>()
    override val localState = MutableStateFlow<LoadingState?>(null)
    override val remoteState = MutableSharedFlow<RoomFrame>()
    private val listeners = mutableListOf<WebSocketClientListener>()

    suspend fun start() {
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
                try {
                    old.block()
                    localState.value = LoadingState.Done
                } catch (e: Exception) {
                    localState.value = LoadingState.Error(e)
                }
            }
        } else {
            null
        }
    }

    private suspend fun connectWebSocketIfNeed(userInfo: UserInfo) {
        val (_, sig) = sessionModel.session ?: return
        if (sig == null) return
        when (connectionHandler.state.value) {
            is LoadingState.Loading -> return
            is LoadingState.Done -> {
                val oldSession = connectionHandler.data.value
                if (oldSession?.isActive == true) return
            }

            is LoadingState.Error -> {}
            null -> {}
        }
        try {
            val session = buildConnection(userInfo, sig)

            startListenerWebSocket(session)
            connectionHandler.done(session)
        } catch (e: Exception) {
            connectionHandler.error(e)
        }
    }

    private fun startListenerWebSocket(session: DefaultClientWebSocketSession) {
        session.launch {
            while (true) {
                try {
                    when (val frame = session.receiveDeserialized<RoomFrame>()) {
                        is RoomFrame.Error -> {
                            remoteState.emit(frame)
                            onMessage(frame)
                        }

                        is RoomFrame.NewTopicInfo -> {
                            val plainFrame = if (frame.topicInfo.content is TopicContent.Encrypted) {
                                val topicInfo = processEncryptedTopic(listOf(frame.topicInfo), sessionModel).first()
                                RoomFrame.NewTopicInfo(topicInfo)
                            } else {
                                frame
                            }
                            onMessage(frame)
                            listeners.forEach {
                                it.onReceived(plainFrame)
                            }
                        }

                        is RoomFrame.Message -> {
                            onMessage(frame)
                        }
                    }
                } catch (_: ClosedReceiveChannelException) {
                    Napier.i(tag = "ClientWebSocket") {
                        "web socket closed"
                    }
                    break
                } catch (_: SocketTimeoutException) {
                    Napier.i(tag = "ClientWebSocket") {
                        "web socket timeout"
                    }
                    break
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    Napier.e(e, tag = "ClientWebSocket") {
                        "startListenerWebSocket failed"
                    }
                }
            }
        }
    }

    override fun addListener(listener: WebSocketClientListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: WebSocketClientListener) {
        listeners.remove(listener)
    }
}
