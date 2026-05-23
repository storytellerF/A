@file:Suppress("unused", "LongMethod")

package com.storyteller_f.a.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.shepeliev.webrtckmp.*
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.client.core.sendFrame
import com.storyteller_f.shared.obj.CustomAnswer
import com.storyteller_f.shared.obj.CustomCandidate
import com.storyteller_f.shared.obj.CustomOffer
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun WebRTCPage(rtcContainer: RTCContainer, roomId: PrimaryKey) {
    val scope = rememberCoroutineScope()
    val localStream by rtcContainer.streamFlow.collectAsState()
    val callingRoom by rtcContainer.callingRoomFlow.collectAsState()
    val localAudioMuted by rtcContainer.localAudioMutedFlow.collectAsState()
    val localVideoMuted by rtcContainer.localVideoMutedFlow.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = padding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val localVideoTrack = localStream?.videoTracks?.firstOrNull()

            localVideoTrack?.takeIf {
                !localVideoMuted
            }?.let {
                Video(videoTrack = it, modifier = Modifier.weight(1f).fillMaxWidth(),)
            } ?: Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (localVideoMuted) "Local camera off" else "Local video")
            }
            RemoteStreamView(rtcContainer)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (localStream == null) {
                    StartButton {
                        scope.launch {
                            val stream = MediaDevices.getUserMedia(audio = true, video = true)
                            rtcContainer.binder.value?.setLocalStream(stream)
                        }
                    }
                } else {
                    StopButton {
                        rtcContainer.binder.value?.let {
                            it.hangup()
                            it.releaseStream()
                        }
                    }

                    SwitchCameraButton {
                        rtcContainer.binder.value?.switchCamera()
                    }
                }
                if (callingRoom == null) {
                    CallButton {
                        rtcContainer.binder.value?.startCall(roomId)
                    }
                } else {
                    HangupButton {
                        rtcContainer.binder.value?.hangup()
                    }
                }
            }

            if (localStream != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioToggleButton(localAudioMuted) {
                        rtcContainer.binder.value?.toggleAudioMuted()
                    }
                    VideoToggleButton(localVideoMuted) {
                        rtcContainer.binder.value?.toggleVideoMuted()
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RemoteStreamView(rtcContainer: RTCContainer) {
    val remotePeers by rtcContainer.remotePeers.collectAsState()
    if (remotePeers.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Remote video")
        }
        return
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        remotePeers.take(4).chunked(2).forEach { rowPeers ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPeers.forEach { peer ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        peer.videoTrack?.takeIf {
                            !peer.videoMuted
                        }?.let {
                            Video(
                                videoTrack = it,
                                audioTrack = peer.audioTrack,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } ?: Text(if (peer.videoMuted) "Camera off" else "Remote video")
                        if (peer.audioMuted) {
                            Text("Muted")
                        }
                    }
                }
                repeat(2 - rowPeers.size) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

suspend fun makeCallByOffer(
    createOffer: RoomFrame.CreateOffer,
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoTrack) -> Unit,
    onRemoteAudioTrack: (AudioTrack) -> Unit = {},
    signalingChannel: SharedFlow<RoomFrame>,
    instance: AccountInstance,
) {
    val roomId = createOffer.roomId
    val targetUid = createOffer.targetUid
    val pc = createRTCPeerConnection()
    // 本地流加到 PeerConnection
    localStream.tracks.forEach { pc.addTrack(it) }
    coroutineScope {
        // 收集 Candidate 并通过信令发送给 Callee
        pc.onIceCandidate
            .onEach { candidate ->
                Napier.d(tag = "web_rtc") { "Caller onIceCandidate: $candidate" }
                instance.sessionManager.webSocketClient.useWebSocket {
                    val customCandidate = CustomCandidate(
                        candidate.sdpMid,
                        candidate.sdpMLineIndex,
                        candidate.candidate,
                    )
                    val sendCandidate = RoomFrame.SendCandidate(customCandidate, roomId, targetUid)
                    sendFrame(sendCandidate)
                }
            }
            .launchIn(this)

        // 远端 Track
        pc.onTrack
            .onEach { event ->
                Napier.d(tag = "web_rtc") { "Caller onTrack: ${event.track?.kind}" }
                event.track?.let {
                    when (it.kind) {
                        MediaStreamTrackKind.Audio -> onRemoteAudioTrack(it as AudioTrack)
                        MediaStreamTrackKind.Video -> onRemoteVideoTrack(it as VideoTrack)
                    }
                }
            }
            .launchIn(this)

        // 创建 Offer
        val offer = pc.createOffer(OfferAnswerOptions(offerToReceiveVideo = true, offerToReceiveAudio = true))
        pc.setLocalDescription(offer)

        // 通过信令发送给 Callee
        val f = RoomFrame.SendOffer(CustomOffer(offer.sdp), roomId, targetUid)
        instance.sessionManager.webSocketClient.useWebSocket {
            sendFrame(f)
        }

        signalingChannel.map {
            it as? RoomFrame.RespondAnswer
        }.filterNotNull().filter {
            it.roomId == roomId && it.uid == targetUid
        }.onEach {
            Napier.i {
                "respond answer ${pc.signalingState} from ${it.uid}"
            }
            if (pc.signalingState == SignalingState.HaveLocalOffer) {
                val answer = SessionDescription(SessionDescriptionType.Answer, it.answer.sdp)
                pc.setRemoteDescription(answer)
            }
        }.launchIn(this)

        signalingChannel.map {
            it as? RoomFrame.ReceiveCandidate
        }.filterNotNull().filter {
            it.roomId == roomId && it.uid == targetUid
        }.onEach {
            val candidate = it.candidate
            pc.addIceCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate))
        }.launchIn(this)
        try {
            awaitCancellation()
        } finally {
            pc.getTransceivers().forEach { pc.removeTrack(it.sender) }
            pc.close()
        }
    }
}

suspend fun makeCallByAnswer(
    createAnswer: RoomFrame.CreateAnswer,
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoTrack) -> Unit,
    onRemoteAudioTrack: (AudioTrack) -> Unit = {},
    signalingChannel: SharedFlow<RoomFrame>,
    instance: AccountInstance,
) {
    val roomId = createAnswer.roomId
    val targetUid = createAnswer.targetUid
    val customOffer = createAnswer.offer
    coroutineScope {
        val pc = createRTCPeerConnection()
        // 本地流加到 PeerConnection（如果需要推流）
        localStream.tracks.forEach { pc.addTrack(it) }
        // 收集 Candidate 并通过信令发送给 Caller
        pc.onIceCandidate
            .onEach { candidate ->
                Napier.d(tag = "web_rtc") { "Callee onIceCandidate: $candidate" }
                candidate
                instance.sessionManager.webSocketClient.useWebSocket {
                    val customCandidate = CustomCandidate(
                        candidate.sdpMid,
                        candidate.sdpMLineIndex,
                        candidate.candidate
                    )
                    val f = RoomFrame.SendCandidate(customCandidate, roomId, targetUid)
                    sendFrame(f)
                }
            }
            .launchIn(this)

        // 远端 Track
        pc.onTrack
            .onEach { event ->
                Napier.d(tag = "web_rtc") { "Callee onTrack: ${event.track?.kind}" }
                event.track?.let {
                    when (it.kind) {
                        MediaStreamTrackKind.Audio -> onRemoteAudioTrack(it as AudioTrack)
                        MediaStreamTrackKind.Video -> onRemoteVideoTrack(it as VideoTrack)
                    }
                }
            }
            .launchIn(this)
        pc.onSignalingStateChange
            .onEach { signalingState ->
                Napier.d(tag = "web_rtc") { "PC2 onSignalingStateChange: $signalingState" }
            }
            .launchIn(this)
        pc.onIceConnectionStateChange
            .onEach { Napier.d(tag = "web_rtc") { "PC2 onIceConnectionStateChange: $it" } }
            .launchIn(this)
        pc.onConnectionStateChange
            .onEach { Napier.d(tag = "web_rtc") { "PC1 onConnectionStateChange: $it" } }
            .launchIn(this)
        pc.setRemoteDescription(SessionDescription(SessionDescriptionType.Offer, customOffer.sdp))
        val answer = pc.createAnswer(options = OfferAnswerOptions())
        pc.setLocalDescription(answer)
        val f = RoomFrame.SendAnswer(CustomAnswer(answer.sdp), roomId, targetUid)
        instance.sessionManager.webSocketClient.useWebSocket {
            sendFrame(f)
        }
        signalingChannel.map {
            it as? RoomFrame.ReceiveCandidate
        }.filterNotNull().filter { frame ->
            (frame.roomId == roomId && frame.uid == targetUid)
        }.onEach { frame ->
            val native = frame.candidate
            val candidate = IceCandidate(native.sdpMid, native.sdpMLineIndex, native.candidate)
            pc.addIceCandidate(candidate)
        }.launchIn(this)
        try {
            awaitCancellation()
        } finally {
            pc.getTransceivers().forEach { pc.removeTrack(it.sender) }
            pc.close()
        }
    }
}

private fun createRTCPeerConnection(): PeerConnection {
    val urls = listOf(
        "stun.l.google.com:19302",
        "stun1.l.google.com:19302",
        "stun2.l.google.com:19302",
        "stun3.l.google.com:19302",
        "stun4.l.google.com:19302",
    )
    return PeerConnection(RtcConfiguration(iceServers = listOf(IceServer(urls))))
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StartButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    ) {
        if (it.all { (_, granted) -> granted }) {
            onClick()
        }
    }

    Button(onClick = {
        if (permissions.allPermissionsGranted) {
            onClick()
        } else {
            val prefs = context.getSharedPreferences("a", Context.MODE_PRIVATE)
            val permissionsRequested = prefs.getBoolean("permissionsRequested", false)
            if (!permissions.shouldShowRationale && permissionsRequested) {
                context.navigateToAppSettings()
                return@Button
            }

            prefs.edit { putBoolean("permissionsRequested", true) }
            permissions.launchMultiplePermissionRequest()
        }
    }, modifier = modifier) {
        Text(CoreStrings.start())
    }
}

private fun Context.navigateToAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        )
    }
    startActivity(intent)
}

@Composable
fun Video(videoTrack: VideoTrack, modifier: Modifier, audioTrack: AudioTrack? = null) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    val lifecycleEventObserver = remember(renderer, videoTrack) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    renderer?.also {
                        it.init(WebRtc.rootEglBase.eglBaseContext, null)
                        videoTrack.addSinkCatching(it)
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    renderer?.also { videoTrack.removeSinkCatching(it) }
                    renderer?.release()
                }

                else -> {
                    // ignore other events
                }
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, lifecycleEventObserver) {
        lifecycle.addObserver(lifecycleEventObserver)

        onDispose {
            renderer?.let { videoTrack.removeSinkCatching(it) }
            renderer?.release()
            lifecycle.removeObserver(lifecycleEventObserver)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                setScalingType(
                    RendererCommon.ScalingType.SCALE_ASPECT_BALANCED,
                    RendererCommon.ScalingType.SCALE_ASPECT_FIT
                )
                renderer = this
            }
        },
    )
}

private fun VideoTrack.addSinkCatching(sink: VideoSink) {
    // runCatching as track may be disposed while activity was in pause
    runCatching { addSink(sink) }
}

private fun VideoTrack.removeSinkCatching(sink: VideoSink) {
    // runCatching as track may be disposed while activity was in pause
    runCatching { removeSink(sink) }
}

@Composable
private fun CallButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier = modifier) {
        Text("Call")
    }
}

@Composable
private fun HangupButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier = modifier) {
        Text("Hangup")
    }
}

@Composable
private fun SwitchCameraButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Switch Camera")
    }
}

@Composable
private fun AudioToggleButton(isMuted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text(if (isMuted) "Unmute" else "Mute")
    }
}

@Composable
private fun VideoToggleButton(isMuted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text(if (isMuted) "Show Video" else "Hide Video")
    }
}

@Composable
private fun StopButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Stop")
    }
}
