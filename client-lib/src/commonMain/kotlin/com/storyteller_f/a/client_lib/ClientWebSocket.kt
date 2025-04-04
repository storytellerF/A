package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

interface ClientWsListener {
    fun onReceived(frame: RoomFrame)
}

@OptIn(DelicateCoroutinesApi::class)
class ClientWebSocket(
    val buildConnection: suspend () -> DefaultClientWebSocketSession,
    val onMessage: suspend (RoomFrame) -> Unit
) {
    val connectionHandler = SimpleLoadingHandler<DefaultClientWebSocketSession> { }
    val localState = MutableStateFlow<LoadingState?>(null)
    val remoteState = MutableSharedFlow<RoomFrame>()
    private val listeners = mutableListOf<ClientWsListener>()

    init {
        GlobalScope.launch {
            combine(LoginViewModel.isAlreadySignUp, LoginViewModel.user) { t1, t2 ->
                t1 && t2 != null
            }.distinctUntilChanged().collect {
                if (it) {
                    while (true) {
                        connectWebSocketIfNeed()
                        delay(5000)
                    }
                } else {
                    connectionHandler.data.value?.cancel()
                }
            }
        }
    }

    fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job? {
        val old = connectionHandler.data.value
        return if (old != null && old.isActive) {
            GlobalScope.launch {
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

    private suspend fun connectWebSocketIfNeed() {
        if (!LoginViewModel.currentIsAlreadySignUp) return
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
            val session = buildConnection()

            startListenerWebSocket(session)
            connectionHandler.done(session)
        } catch (e: Exception) {
            connectionHandler.error(e)
        }
    }

    private fun startListenerWebSocket(session: DefaultClientWebSocketSession) {
        GlobalScope.launch {
            runCatching {
                while (true) {
                    when (val frame = session.receiveDeserialized<RoomFrame>()) {
                        is RoomFrame.Error -> {
                            remoteState.emit(frame)
                            onMessage(frame)
                        }

                        is RoomFrame.NewTopicInfo -> {
                            val plainFrame = if (frame.topicInfo.content is TopicContent.Encrypted) {
                                val topicInfo = processEncryptedTopic(listOf(frame.topicInfo)).first()
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
                }
            }.onFailure {
                if (it is ClosedReceiveChannelException) {
                    Napier.i(tag = "ClientWebSocket") {
                        "Server closed"
                    }
                } else {
                    Napier.e(it, tag = "ClientWebSocket") {
                        "Exception in startListenerWebSocket"
                    }
                }
            }
        }
    }

    fun addListener(listener: ClientWsListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ClientWsListener) {
        listeners.remove(listener)
    }
}
