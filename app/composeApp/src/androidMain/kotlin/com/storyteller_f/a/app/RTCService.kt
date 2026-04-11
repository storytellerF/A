package com.storyteller_f.a.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import com.shepeliev.webrtckmp.AudioTrack
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.VideoTrack
import com.shepeliev.webrtckmp.videoTracks
import com.storyteller_f.a.client.core.sendFrame
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class RTCService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        val channel = "Upload"
        getOrCreateNotificationChannel(this, channel)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.storyteller_f.a.app.android_library.R.drawable.baseline_video_call_24)
            .setContentTitle("RTC")
            .setOngoing(true)
        startForeground(2, notification.build())
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        val rtcHandle = DefaultRTCHandle(uiViewModel, lifecycle)
        return RTCBinder(rtcHandle)
    }
}

data class RemotePeerState(
    val uid: PrimaryKey,
    val audioTrack: AudioTrack? = null,
    val videoTrack: VideoTrack? = null,
)

interface RTCHandle {
    val stream: StateFlow<MediaStream?>
    val callingRoom: StateFlow<PrimaryKey?>
    val remotePeers: StateFlow<Map<PrimaryKey, RemotePeerState>>
    var job: Job?
    fun startCall(roomId: PrimaryKey)
    fun setLocalStream(stream: MediaStream)
    fun releaseStream()
    fun switchCamera()
    fun hangup()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRTCHandle(val uiViewModel: UIViewModel, val lifecycle: Lifecycle) : RTCHandle {
    override val callingRoom = MutableStateFlow<PrimaryKey?>(null)
    override var stream = MutableStateFlow<MediaStream?>(null)
    override val remotePeers = MutableStateFlow<Map<PrimaryKey, RemotePeerState>>(emptyMap())
    override var job: Job? = null
    private val peerJobs = mutableMapOf<PrimaryKey, Job>()

    init {
        lifecycle.coroutineScope.launch {
            combine(callingRoom, uiViewModel.instance, uiViewModel.instance.flatMapLatest {
                it.sessionManager.webSocketClient.frameFlow
            }) { r, i, f ->
                Triple(r, i, f)
            }.collectLatest { (room, instance, frame) ->
                when (frame) {
                    is RoomFrame.CreateOffer -> {
                        processCreateOffer(frame, instance)
                    }

                    is RoomFrame.CreateAnswer -> {
                        processCreateAnswer(frame, instance)
                    }

                    is RoomFrame.PeerLeft -> {
                        processPeerLeft(frame)
                    }

                    else -> {}
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
        val roomId = callingRoom.value
        peerJobs.values.toList().forEach {
            it.cancel()
        }
        peerJobs.clear()
        job?.cancel()
        remotePeers.value = emptyMap()
        callingRoom.value = null
        if (roomId != null) {
            val session = uiViewModel.instance.value.sessionManager
            lifecycle.coroutineScope.launch {
                session.proxy.webSocketClient.useWebSocket {
                    sendFrame(RoomFrame.StopCall(roomId))
                }
            }
        }
    }

    override fun startCall(roomId: PrimaryKey) {
        if (callingRoom.value == roomId) {
            return
        }
        val session = uiViewModel.instance.value.sessionManager
        lifecycle.coroutineScope.launch {
            session.proxy.webSocketClient.useWebSocket {
                val f = RoomFrame.StartCall(roomId)
                sendFrame(f)
            }
        }
        callingRoom.value = roomId
    }

    private fun processCreateAnswer(
        frame: RoomFrame.CreateAnswer,
        instance: AccountInstance,
    ) {
        if (callingRoom.value != frame.roomId) {
            return
        }
        if (peerJobs[frame.targetUid]?.isActive == true) {
            return
        }
        val localStream = stream.value ?: return
        val signalingChannel = instance.sessionManager.webSocketClient.frameFlow
        val targetUid = frame.targetUid
        val currentJob = lifecycle.coroutineScope.launch {
            makeCallByAnswer(
                frame,
                localStream,
                onRemoteVideoTrack = { setRemoteVideoTrack(targetUid, it) },
                onRemoteAudioTrack = { setRemoteAudioTrack(targetUid, it) },
                signalingChannel = signalingChannel,
                instance = instance,
            )
        }
        peerJobs[targetUid] = currentJob
        job = currentJob
        currentJob.invokeOnCompletion {
            peerJobs.remove(targetUid)
            removeRemotePeer(targetUid)
        }
    }

    private fun processCreateOffer(
        frame: RoomFrame.CreateOffer,
        instance: AccountInstance
    ) {
        if (callingRoom.value != frame.roomId) {
            return
        }
        if (peerJobs[frame.targetUid]?.isActive == true) {
            return
        }
        val localStream = stream.value ?: return
        val signalingChannel = instance.sessionManager.webSocketClient.frameFlow
        val targetUid = frame.targetUid
        val currentJob = lifecycle.coroutineScope.launch {
            makeCallByOffer(
                frame,
                localStream,
                onRemoteVideoTrack = { setRemoteVideoTrack(targetUid, it) },
                onRemoteAudioTrack = { setRemoteAudioTrack(targetUid, it) },
                signalingChannel = signalingChannel,
                instance = instance,
            )
        }
        peerJobs[targetUid] = currentJob
        job = currentJob
        currentJob.invokeOnCompletion {
            peerJobs.remove(targetUid)
            removeRemotePeer(targetUid)
        }
    }

    private fun setRemoteAudioTrack(uid: PrimaryKey, audioTrack: AudioTrack) {
        Napier.i {
            "setRemoteAudioTrack $uid"
        }
        val updated = remotePeers.value.toMutableMap()
        val current = updated[uid] ?: RemotePeerState(uid = uid)
        updated[uid] = current.copy(audioTrack = audioTrack)
        remotePeers.value = updated
    }

    private fun setRemoteVideoTrack(uid: PrimaryKey, videoTrack: VideoTrack) {
        Napier.i {
            "setRemoteVideoTrack $uid"
        }
        val updated = remotePeers.value.toMutableMap()
        val current = updated[uid] ?: RemotePeerState(uid = uid)
        updated[uid] = current.copy(videoTrack = videoTrack)
        remotePeers.value = updated
    }

    private fun processPeerLeft(frame: RoomFrame.PeerLeft) {
        if (callingRoom.value != frame.roomId) {
            return
        }
        peerJobs.remove(frame.uid)?.cancel()
        removeRemotePeer(frame.uid)
    }

    private fun removeRemotePeer(uid: PrimaryKey) {
        remotePeers.value = remotePeers.value.toMutableMap().apply {
            remove(uid)
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
    val streamFlow: StateFlow<MediaStream?>
    val callingRoomFlow: StateFlow<Long?>
    val remotePeers: StateFlow<List<RemotePeerState>>
}
