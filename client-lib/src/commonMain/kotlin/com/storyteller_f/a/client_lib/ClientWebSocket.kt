package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

interface ClientWsListener {
    fun onReceived(frame: RoomFrame)
}

interface ClientWebSocket {
    val connectionHandler: LoadingHandler<DefaultClientWebSocketSession>
    val localState: StateFlow<LoadingState?>
    val remoteState: SharedFlow<RoomFrame>
    fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job?
    fun addListener(listener: ClientWsListener)
    fun removeListener(listener: ClientWsListener)

    companion object {
        val EMPTY = object : ClientWebSocket {
            override val connectionHandler: LoadingHandler<DefaultClientWebSocketSession>
                get() = TODO("Not yet implemented")
            override val localState: StateFlow<LoadingState?>
                get() = TODO("Not yet implemented")
            override val remoteState: SharedFlow<RoomFrame>
                get() = TODO("Not yet implemented")

            override fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job? {
                TODO("Not yet implemented")
            }

            override fun addListener(listener: ClientWsListener) {
                TODO("Not yet implemented")
            }

            override fun removeListener(listener: ClientWsListener) {
                TODO("Not yet implemented")
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
class ClientWebSocketImpl(
    val client: HttpClient,
    val buildConnection: suspend HttpClient.(UserInfo, String) -> DefaultClientWebSocketSession,
    val onMessage: suspend (RoomFrame) -> Unit
) : ClientWebSocket {
    override val connectionHandler = SimpleLoadingHandler<DefaultClientWebSocketSession> { }
    override val localState = MutableStateFlow<LoadingState?>(null)
    override val remoteState = MutableSharedFlow<RoomFrame>()
    private val listeners = mutableListOf<ClientWsListener>()

    init {
        GlobalScope.launch {
            combine(SignInViewModel.state, SignInViewModel.user) { t1, t2 ->
                t1 to t2
            }.distinctUntilChanged().collect { (t1, t2) ->
                when {
                    t1 !is ClientSession.SignInSuccess -> connectionHandler.data.value?.cancel()
                    t2 == null -> SignInViewModel.retryLogin(client)
                    else -> while (true) {
                        connectWebSocketIfNeed(t2)
                        delay(5000)
                    }
                }
            }
        }
    }

    override fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit): Job? {
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

    private suspend fun connectWebSocketIfNeed(t2: UserInfo) {
        val (_, sig) = SignInViewModel.session ?: return
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
            val session = client.buildConnection(t2, sig)

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

    override fun addListener(listener: ClientWsListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ClientWsListener) {
        listeners.remove(listener)
    }
}
