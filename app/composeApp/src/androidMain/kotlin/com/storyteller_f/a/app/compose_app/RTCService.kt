package com.storyteller_f.a.app.compose_app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.videoTracks
import com.storyteller_f.a.client.core.WebSocketClientListener
import com.storyteller_f.a.client.core.sendFrame
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class RTCService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        val channel = "Upload"
        getOrCreateNotificationManager(this, channel)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.storyteller_f.a.app.R.drawable.baseline_video_call_24)
            .setContentTitle("RTC")
            .setOngoing(true)
        startForeground(2, notification.build())
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        val uiViewModel = (application as AApplication).uiViewModel
        val rtcHandle = DefaultRTCHandle(uiViewModel, lifecycle)
        return RTCBinder(rtcHandle)
    }
}

interface RTCHandle {
    val stream: StateFlow<MediaStream?>
    val callingRoom: StateFlow<PrimaryKey?>
    var job: Job?
    fun startCall(roomId: PrimaryKey)
    fun setLocalStream(stream: MediaStream)
    fun releaseStream()
    fun switchCamera()
    fun hangup()
}

class DefaultRTCHandle(val uiViewModel: UIViewModel, val lifecycle: Lifecycle) : RTCHandle {
    override val callingRoom = MutableStateFlow<PrimaryKey?>(null)
    override var stream = MutableStateFlow<MediaStream?>(null)
    override var job: Job? = null

    val signalingChannel = MutableSharedFlow<RoomFrame>()

    init {
        lifecycle.coroutineScope.launch {
            combine(callingRoom, uiViewModel.instance) { r, i ->
                r to i
            }.collectLatest { (room, instance) ->
                val listener = object : WebSocketClientListener {
                    override suspend fun onReceived(
                        frame: RoomFrame,
                        session: DefaultClientWebSocketSession
                    ) {
                        when (frame) {
                            is RoomFrame.CreateOffer -> {
                                processCreateOffer(frame, instance)
                            }

                            is RoomFrame.CreateAnswer -> {
                                processCreateAnswer(frame, instance)
                            }

                            is RoomFrame.RespondAnswer -> {
                                signalingChannel.emit(frame)
                            }

                            is RoomFrame.ReceiveCandidate -> {
                                signalingChannel.emit(frame)
                            }

                            else -> {}
                        }
                    }
                }
                instance.manager.webSocketClient.addListener(listener)
                try {
                    awaitCancellation()
                } finally {
                    instance.manager.webSocketClient.removeListener(listener)
                }
            }
        }
    }

    override fun setLocalStream(stream: MediaStream) {
        this.stream.value = stream
    }

    override fun releaseStream() {
        stream.value?.release()
        stream.value = null
    }

    override fun switchCamera() {
        val mediaStream = stream.value ?: return
        lifecycle.coroutineScope.launch {
            mediaStream.videoTracks.firstOrNull()?.switchCamera()
        }
    }

    override fun hangup() {
        job?.cancel()
    }

    override fun startCall(roomId: PrimaryKey) {
        val session = uiViewModel.instance.value.manager
        session.manager.webSocketClient.useWebSocket {
            val f = RoomFrame.StartCall(roomId)
            sendFrame(f)
        }
    }

    private fun processCreateAnswer(
        frame: RoomFrame.CreateAnswer,
        instance: AccountInstance,
    ) {
        val localStream = stream.value ?: return
        job = lifecycle.coroutineScope.launch {
            makeCallByAnswer(frame, localStream, {}, {}, signalingChannel, instance)
        }
    }

    private fun processCreateOffer(
        frame: RoomFrame.CreateOffer,
        instance: AccountInstance
    ) {
        val localStream = stream.value ?: return
        job = lifecycle.coroutineScope.launch {
            makeCallByOffer(frame, localStream, {}, {}, signalingChannel, instance)
        }
    }
}

class RTCBinder(val rtcHandle: RTCHandle) : Binder(), RTCHandle by rtcHandle

class RTCServiceConnection(val rtcActivity: WeakReference<RTCContainer>) : ServiceConnection {
    override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
        val activity = rtcActivity.get() ?: return
        val binder = p1 as? RTCBinder ?: return
        activity.binder.value = binder
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
        val activity = rtcActivity.get() ?: return
        activity.binder.value = null
    }
}

interface RTCContainer {
    val binder: MutableStateFlow<RTCHandle?>
}
