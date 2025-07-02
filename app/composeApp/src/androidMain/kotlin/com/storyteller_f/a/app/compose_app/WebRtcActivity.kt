@file:Suppress("unused", "LongMethod")

package com.storyteller_f.a.app.compose_app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
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
import io.github.aakira.napier.Napier
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink

class WebRtcActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commonForActivity()
        setContent {
            WebRtcPage()
        }
    }
}

@Composable
fun WebRtcPage() {
    val scope = rememberCoroutineScope()
    val (localStream, setLocalStream) = remember { mutableStateOf<MediaStream?>(null) }
    val (remoteVideoTrack, setRemoteVideoTrack) = remember {
        mutableStateOf<VideoStreamTrack?>(
            null
        )
    }
    val (remoteAudioTrack, setRemoteAudioTrack) = remember {
        mutableStateOf<AudioStreamTrack?>(
            null
        )
    }
    val (peerConnections, setPeerConnections) = remember {
        mutableStateOf<Pair<PeerConnection, PeerConnection>?>(null)
    }

    LaunchedEffect(localStream, peerConnections) {
        if (peerConnections == null || localStream == null) return@LaunchedEffect
        makeCall(peerConnections, localStream, setRemoteVideoTrack, setRemoteAudioTrack)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val localVideoTrack = localStream?.videoTracks?.firstOrNull()

        localVideoTrack?.let {
            Video(
                videoTrack = it,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } ?: Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material.Text("Local video")
        }

        remoteVideoTrack?.let {
            Video(
                videoTrack = it,
                audioTrack = remoteAudioTrack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } ?: Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material.Text("Remote video")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (localStream == null) {
                StartButton(onClick = {
                    scope.launch {
                        val stream = MediaDevices.getUserMedia(audio = true, video = true)
                        setLocalStream(stream)
                    }
                })
            } else {
                StopButton(
                    onClick = {
                        hangup(peerConnections)
                        localStream.release()
                        setLocalStream(null)
                        setPeerConnections(null)
                        setRemoteVideoTrack(null)
                        setRemoteAudioTrack(null)
                    }
                )

                SwitchCameraButton(
                    onClick = {
                        scope.launch { localStream.videoTracks.firstOrNull()?.switchCamera() }
                    }
                )
            }
            if (peerConnections == null) {
                CallButton(
                    onClick = { setPeerConnections(Pair(PeerConnection(), PeerConnection())) },
                )
            } else {
                HangupButton(onClick = {
                    hangup(peerConnections)
                    setPeerConnections(null)
                    setRemoteVideoTrack(null)
                    setRemoteAudioTrack(null)
                })
            }
        }
    }
}

fun hangup(peerConnections: Pair<PeerConnection, PeerConnection>?) {
    val (pc1, pc2) = peerConnections ?: return
    pc1.getTransceivers().forEach { pc1.removeTrack(it.sender) }
    pc1.close()
    pc2.close()
}

suspend fun makeCall(
    peerConnections: Pair<PeerConnection, PeerConnection>,
    localStream: MediaStream,
    onRemoteVideoTrack: (VideoStreamTrack) -> Unit,
    onRemoteAudioTrack: (AudioStreamTrack) -> Unit = {},
): Nothing = coroutineScope {
    val (pc1, pc2) = peerConnections
    localStream.tracks.forEach { pc1.addTrack(it) }
    val pc1IceCandidates = mutableListOf<IceCandidate>()
    val pc2IceCandidates = mutableListOf<IceCandidate>()
    pc1.onIceCandidate
        .onEach { Napier.d(tag = "web_rtc") { "PC1 onIceCandidate: $it" } }
        .onEach {
            if (pc2.signalingState == SignalingState.HaveRemoteOffer) {
                pc2.addIceCandidate(it)
            } else {
                pc1IceCandidates.add(it)
            }
        }
        .launchIn(this)
    pc2.onIceCandidate
        .onEach { Napier.d(tag = "web_rtc") { "PC2 onIceCandidate: $it" } }
        .onEach {
            if (pc1.signalingState == SignalingState.HaveRemoteOffer) {
                pc1.addIceCandidate(it)
            } else {
                pc2IceCandidates.add(it)
            }
        }
        .launchIn(this)
    pc1.onSignalingStateChange
        .onEach { signalingState ->
            Napier.d(tag = "web_rtc") { "PC1 onSignalingStateChange: $signalingState" }
            if (signalingState == SignalingState.HaveRemoteOffer) {
                pc2IceCandidates.forEach { pc1.addIceCandidate(it) }
                pc2IceCandidates.clear()
            }
        }
        .launchIn(this)
    pc2.onSignalingStateChange
        .onEach { signalingState ->
            Napier.d(tag = "web_rtc") { "PC2 onSignalingStateChange: $signalingState" }
            if (signalingState == SignalingState.HaveRemoteOffer) {
                pc1IceCandidates.forEach { pc2.addIceCandidate(it) }
                pc1IceCandidates.clear()
            }
        }
        .launchIn(this)
    pc1.onIceConnectionStateChange
        .onEach { Napier.d(tag = "web_rtc") { "PC1 onIceConnectionStateChange: $it" } }
        .launchIn(this)
    pc2.onIceConnectionStateChange
        .onEach { Napier.d(tag = "web_rtc") { "PC2 onIceConnectionStateChange: $it" } }
        .launchIn(this)
    pc1.onConnectionStateChange
        .onEach { Napier.d(tag = "web_rtc") { "PC1 onConnectionStateChange: $it" } }
        .launchIn(this)
    pc2.onConnectionStateChange
        .onEach { Napier.d(tag = "web_rtc") { "PC2 onConnectionStateChange: $it" } }
        .launchIn(this)
    pc1.onTrack
        .onEach { Napier.d(tag = "web_rtc") { "PC1 onTrack: $it" } }
        .launchIn(this)
    pc2.onTrack
        .onEach { Napier.d(tag = "web_rtc") { "PC2 onTrack: ${it.track?.kind}" } }
        .map { it.track }
        .filterNotNull()
        .onEach {
            if (it.kind == MediaStreamTrackKind.Audio) {
                onRemoteAudioTrack(it as AudioStreamTrack)
            } else if (it.kind == MediaStreamTrackKind.Video) {
                onRemoteVideoTrack(it as VideoStreamTrack)
            }
        }
        .launchIn(this)
    val offer = pc1.createOffer(OfferAnswerOptions(offerToReceiveVideo = true, offerToReceiveAudio = true))
    pc1.setLocalDescription(offer)
    pc2.setRemoteDescription(offer)
    val answer = pc2.createAnswer(options = OfferAnswerOptions())
    pc2.setLocalDescription(answer)
    pc1.setRemoteDescription(answer)

    awaitCancellation()
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
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
        Text("Start")
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
fun Video(videoTrack: VideoStreamTrack, modifier: Modifier, audioTrack: AudioStreamTrack? = null) {
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

private fun VideoStreamTrack.addSinkCatching(sink: VideoSink) {
    // runCatching as track may be disposed while activity was in pause
    runCatching { addSink(sink) }
}

private fun VideoStreamTrack.removeSinkCatching(sink: VideoSink) {
    // runCatching as track may be disposed while activity was in pause
    runCatching { removeSink(sink) }
}

@Composable
private fun CallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material.Button(onClick, modifier = modifier) {
        Text("Call")
    }
}

@Composable
private fun HangupButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier = modifier) {
        Text("Hangup")
    }
}

@Composable
private fun SwitchCameraButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Switch Camera")
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("Stop")
    }
}
