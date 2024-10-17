package com.storyteller_f.a.client_lib

import com.storyteller_f.shared.obj.RoomFrame
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(DelicateCoroutinesApi::class)
class ClientWebSocket(
    val buildConnection: suspend () -> DefaultClientWebSocketSession,
    val onMessage: suspend (RoomFrame) -> Unit
) {
    val connectionHandler = LoadingHandler<DefaultClientWebSocketSession> { }
    val localState = MutableStateFlow<LoadingState?>(null)
    val remoteState = MutableSharedFlow<RoomFrame>()

    init {
        GlobalScope.launch {
            while (true) {
                connectWebSocketIfNeed()
                delay(5000)
            }
        }
    }

    fun useWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit) {
        val old = connectionHandler.data.value
        if (old != null && old.isActive) {
            GlobalScope.launch {
                localState.value = LoadingState.Loading("send")
                try {
                    old.block()
                    localState.value = LoadingState.Done()
                } catch (e: Exception) {
                    localState.value = LoadingState.Error(e)
                }
            }
        }
    }

    private suspend fun connectWebSocketIfNeed() {
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
            connectionHandler.data.value = null
            connectionHandler.state.value = LoadingState.Error(e)
        }
    }

    private fun startListenerWebSocket(session: DefaultClientWebSocketSession) {
        GlobalScope.launch {
            runCatching {
                while (true) {
                    val frame = session.receiveDeserialized<RoomFrame>()
                    onMessage(frame)

                    remoteState.emit(frame)
                }
            }.onFailure {
                Napier.e(it, tag = "pagination") {
                    "Exception in Client WebSocket"
                }
                connectionHandler.data.value = null
                connectionHandler.state.value = null
            }
        }
    }
}
