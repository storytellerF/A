/*
 * This is a private project. All rights reserved.
 */

@file:Suppress("unused", "LongMethod")

package com.storyteller_f.a.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.shepeliev.webrtckmp.AudioTrack
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.IceServer
import com.shepeliev.webrtckmp.MediaStream
import com.shepeliev.webrtckmp.MediaStreamTrackKind
import com.shepeliev.webrtckmp.OfferAnswerOptions
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.SignalingState
import com.shepeliev.webrtckmp.VideoTrack
import com.shepeliev.webrtckmp.WebRtc
import com.shepeliev.webrtckmp.onConnectionStateChange
import com.shepeliev.webrtckmp.onIceCandidate
import com.shepeliev.webrtckmp.onIceConnectionStateChange
import com.shepeliev.webrtckmp.onSignalingStateChange
import com.shepeliev.webrtckmp.onTrack
import com.shepeliev.webrtckmp.videoTracks
import com.storyteller_f.a.client.core.sendFrame
import com.storyteller_f.shared.obj.CustomAnswer
import com.storyteller_f.shared.obj.CustomCandidate
import com.storyteller_f.shared.obj.CustomOffer
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink

@Composable
fun WebRTCPage(rtcContainer: RTCContainer, roomId: PrimaryKey) {
    val localStream by rtcContainer.streamFlow.collectAsStateWithLifecycle()
    val callingRoom by rtcContainer.callingRoomFlow.collectAsStateWithLifecycle()
    val peers by rtcContainer.remotePeers.collectAsStateWithLifecycle()
    val audioMuted by rtcContainer.localAudioMutedFlow.collectAsStateWithLifecycle()
    val videoMuted by rtcContainer.localVideoMutedFlow.collectAsStateWithLifecycle()
    val connected by rtcContainer.binder.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Room $roomId", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (callingRoom == roomId) "${peers.size + 1} participants · In call" else "Preview · Ready to join",
                style = MaterialTheme.typography.bodyMedium,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "local") {
                    ParticipantTile("You", localStream?.videoTracks?.firstOrNull(), videoMuted, audioMuted)
                }
                items(peers, key = { it.uid }) { peer ->
                    ParticipantTile("Participant ${peer.uid}", peer.videoTrack, peer.videoMuted, peer.audioMuted)
                }
                if (callingRoom == roomId && peers.isEmpty()) {
                    item(key = "waiting") {
                        Card(Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("Waiting for others to join this room")
                            }
                        }
                    }
                }
            }
            val handle = connected
            if (handle != null) {
                val error by handle.error.collectAsStateWithLifecycle()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            CallControls(rtcContainer, roomId, localStream != null, callingRoom != null, connected != null)
        }
    }
}

@Composable
private fun ParticipantTile(label: String, track: VideoTrack?, videoMuted: Boolean, audioMuted: Boolean) {
    Card(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(Modifier.fillMaxSize()) {
            if (track != null && !videoMuted) {
                Video(track, Modifier.fillMaxSize())
            } else {
                Text(
                    if (videoMuted) "Camera off" else "Camera preview",
                    Modifier.align(Alignment.Center),
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Text(
                    if (audioMuted) "$label · Muted" else label,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CallControls(
    container: RTCContainer,
    roomId: PrimaryKey,
    hasStream: Boolean,
    inCall: Boolean,
    connected: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasStream) {
            val audioMuted by container.localAudioMutedFlow.collectAsStateWithLifecycle()
            val videoMuted by container.localVideoMutedFlow.collectAsStateWithLifecycle()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioToggleButton(audioMuted, Modifier.weight(1f)) { container.binder.value?.toggleAudioMuted() }
                VideoToggleButton(videoMuted, Modifier.weight(1f)) { container.binder.value?.toggleVideoMuted() }
            }
            SwitchCameraButton(Modifier.fillMaxWidth()) { container.binder.value?.switchCamera() }
        }
        if (inCall) {
            HangupButton(Modifier.fillMaxWidth()) {
                container.binder.value?.let {
                    it.hangup()
                    it.releaseStream()
                }
            }
        } else if (connected) {
            StartButton(Modifier.fillMaxWidth()) { container.binder.value?.startCall(roomId) }
        } else {
            Text("Connecting to call service…")
        }
    }
}

suspend fun makeCallByOffer(
    createOffer: RoomFrame.CreateOffer,
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoTrack) -> Unit,
    onRemoteAudioTrack: (AudioTrack) -> Unit = {},
    signaling: PeerSignaling,
    instance: IAccountInstance,
) {
    val roomId = createOffer.roomId
    val targetUid = createOffer.targetUid
    val pc = createRTCPeerConnection()
    try {
        // 本地流加到 PeerConnection
        localStream.tracks.forEach { pc.addTrack(it) }
        coroutineScope {
            // 收集 Candidate 并通过信令发送给 Callee
            pc.onIceCandidate
                .onEach { candidate ->
                    instance.sessionManager.webSocketClient.useWebSocket {
                        val customCandidate =
                            CustomCandidate(
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
                    Napier.d(tag = "web_rtc") { "Caller onTrack: ${event.track?.kind ?: "<none>"}" }
                    event.track?.let {
                        when (it.kind) {
                            MediaStreamTrackKind.Audio -> onRemoteAudioTrack(it as AudioTrack)
                            MediaStreamTrackKind.Video -> onRemoteVideoTrack(it as VideoTrack)
                        }
                    }
                }
                .launchIn(this)

            // 创建 Offer
            val offer =
                pc.createOffer(
                    OfferAnswerOptions(
                        offerToReceiveVideo = true,
                        offerToReceiveAudio = true,
                    ),
                )
            pc.setLocalDescription(offer)

            // 通过信令发送给 Callee
            val f = RoomFrame.SendOffer(CustomOffer(offer.sdp), roomId, targetUid)
            instance.sessionManager.webSocketClient.useWebSocket {
                sendFrame(f)
            }

            val answerFrame = signaling.answer.await()
            Napier.i {
                "respond answer ${pc.signalingState} from ${answerFrame.uid}"
            }
            if (pc.signalingState == SignalingState.HaveLocalOffer) {
                val answer = SessionDescription(SessionDescriptionType.Answer, answerFrame.answer.sdp)
                pc.setRemoteDescription(answer)
            }
            signaling.candidates
                .onEach { frame ->
                    pc.addCustomIceCandidate(frame.candidate)
                }
                .launchIn(this)
            awaitCancellation()
        }
    } finally {
        pc.close()
    }
}

suspend fun makeCallByAnswer(
    createAnswer: RoomFrame.CreateAnswer,
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoTrack) -> Unit,
    onRemoteAudioTrack: (AudioTrack) -> Unit = {},
    signaling: PeerSignaling,
    instance: IAccountInstance,
) {
    val roomId = createAnswer.roomId
    val targetUid = createAnswer.targetUid
    val customOffer = createAnswer.offer
    val pc = createRTCPeerConnection()
    try {
        coroutineScope {
            // 本地流加到 PeerConnection（如果需要推流）
            localStream.tracks.forEach { pc.addTrack(it) }
            // 收集 Candidate 并通过信令发送给 Caller
            pc.onIceCandidate
                .onEach { candidate ->
                    instance.sessionManager.webSocketClient.useWebSocket {
                        val customCandidate =
                            CustomCandidate(
                                candidate.sdpMid,
                                candidate.sdpMLineIndex,
                                candidate.candidate,
                            )
                        val f = RoomFrame.SendCandidate(customCandidate, roomId, targetUid)
                        sendFrame(f)
                    }
                }
                .launchIn(this)

            // 远端 Track
            pc.onTrack
                .onEach { event ->
                    Napier.d(tag = "web_rtc") { "Callee onTrack: ${event.track?.kind ?: "<none>"}" }
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
            signaling.candidates
                .onEach { frame ->
                    pc.addCustomIceCandidate(frame.candidate)
                }
                .launchIn(this)
            awaitCancellation()
        }
    } finally {
        pc.close()
    }
}

private suspend fun PeerConnection.addCustomIceCandidate(candidate: CustomCandidate) {
    addIceCandidate(
        IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.candidate,
        ),
    )
}

private fun createRTCPeerConnection(): PeerConnection {
    val urls =
        listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
        )
    return PeerConnection(RtcConfiguration(iceServers = listOf(IceServer(urls))))
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StartButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current

    val permissions =
        rememberMultiplePermissionsState(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
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
        Text("Join call")
    }
}

private fun Context.navigateToAppSettings() {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        }
    startActivity(intent)
}

@Composable
fun Video(videoTrack: VideoTrack, modifier: Modifier) {
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, renderer, videoTrack) {
        var initialized = false
        fun resumeRenderer() {
            if (!initialized) {
                renderer.init(WebRtc.rootEglBase.eglBaseContext, null)
                videoTrack.addSinkCatching(renderer)
                initialized = true
            }
        }
        fun pauseRenderer() {
            if (initialized) {
                videoTrack.removeSinkCatching(renderer)
                renderer.release()
                initialized = false
            }
        }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> resumeRenderer()
                    Lifecycle.Event.ON_PAUSE -> pauseRenderer()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeRenderer()
        onDispose {
            lifecycle.removeObserver(observer)
            pauseRenderer()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            renderer.apply {
                setScalingType(
                    RendererCommon.ScalingType.SCALE_ASPECT_BALANCED,
                    RendererCommon.ScalingType.SCALE_ASPECT_FIT,
                )
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
private fun HangupButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier = modifier) {
        Text("Leave call")
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
