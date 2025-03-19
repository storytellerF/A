package com.storyteller_f.a.app.compontents

import android.app.PictureInPictureParams
import android.content.*
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.util.Consumer
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.dokar.sonner.ToasterState
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.MediaProvider
import com.storyteller_f.shared.model.MediaInfo
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Constant for broadcast receiver
const val ACTION_BROADCAST_CONTROL = "broadcast_control"

// Intent extras for broadcast controls from Picture-in-Picture mode.
const val EXTRA_CONTROL_TYPE = "control_type"
const val EXTRA_CONTROL_PLAY = 1
const val EXTRA_CONTROL_PAUSE = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
actual fun VideoView(
    id: String,
    contentType: String,
    playList: List<PlayItem>,
    coverMediaInfo: MediaInfo?
) {
    val uuid = rememberSaveable {
        Uuid.random()
    }
    val currentSession = MediaPlayerSession(uuid, id, contentType, playList, coverMediaInfo)

    log {
        "Video $uuid recomposing"
    }
    val player = MediaProvider.controller ?: return
    val playingSession by savedSession
    val isPip = rememberIsInPipMode()
    LaunchedEffect(playingSession, currentSession) {
        playingSession?.let {
            if (it.uuid == null && it.id == currentSession.id || isPip) {
                MediaProvider.switch(it.copy(uuid = uuid))
            }
        }
    }
    DisposableEffect(null) {
        onDispose {
            Napier.d {
                "Video $uuid dispose $isPip"
            }
            MediaProvider.releaseView(currentSession)
        }
    }
    var showSheet by remember {
        mutableStateOf(false)
    }
    if (isPip) {
        VideoPlayer(playingSession, currentSession, player, contentType, coverMediaInfo, playList)
    } else {
        CodeBlock {
            VideoPlayer(playingSession, currentSession, player, contentType, coverMediaInfo, playList)
            VideoOpRow(playList, id, contentType, currentSession == playingSession) {
                showSheet = false
            }
        }
    }
    val sheetState = rememberModalBottomSheetState()
    VideoPlaylistPicker(showSheet, sheetState, {
        showSheet = false
    }, playList) { _, i ->
        player.seekTo(i, 0)
        player.play()
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun VideoPlayer(
    playingSession: MediaPlayerSession?,
    currentSession: MediaPlayerSession,
    player: MediaController,
    contentType: String,
    coverMediaInfo: MediaInfo?,
    playList: List<PlayItem>
) {
    val isPip = rememberIsInPipMode()
    val ratio = if (playingSession?.videoSize != null && playingSession.uuid == currentSession.uuid && isPip) {
        playingSession.videoSize.width.toFloat() / playingSession.videoSize.height
    } else {
        16f / 9
    }
    Napier.d {
        "Video ${currentSession.uuid} ratio $ratio $playingSession $currentSession $isPip"
    }
    Box(modifier = Modifier.aspectRatio(ratio)) {
        when {
            playingSession == null -> PlayerWaiting(coverMediaInfo, currentSession, playList, contentType)
            playingSession.uuid == currentSession.uuid -> AndroidPlayer(currentSession, player, contentType)
            playingSession.id == currentSession.id -> PlayerOccupy(currentSession)
            else -> PlayerWaiting(coverMediaInfo, currentSession, playList, contentType)
        }
    }
}

@Composable
private fun BoxScope.PlayerOccupy(currentSession: MediaPlayerSession) {
    IconButton({
        MediaProvider.switch(currentSession)
    }, modifier = Modifier.Companion.align(Alignment.Center)) {
        Icon(Icons.Default.TapAndPlay, "return")
    }
}

@Composable
private fun BoxScope.PlayerWaiting(
    coverMediaInfo: MediaInfo?,
    uuid: MediaPlayerSession,
    playList: List<PlayItem>,
    contentType: String
) {
    if (coverMediaInfo != null) {
        val request = imageRequestInMarkdown(coverMediaInfo)
        AsyncImage(request, contentDescription = "cover", modifier = Modifier.fillMaxSize())
    }
    IconButton({
        MediaProvider.get(uuid, { player ->
            player.playNewMedia(playList, contentType)
        }, {
            Napier.i {
                "Video release $uuid"
            }
        })
    }, modifier = Modifier.Companion.align(Alignment.Center)) {
        Icon(Icons.Default.PlayArrow, "play")
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun BoxScope.AndroidPlayer(
    currentSession: MediaPlayerSession,
    player: MediaController,
    contentType: String
) {
    val (currentLoading, currentIsPlaying) = player(player, currentSession)
    // [8, 12]
    val pipModifier = buildPlayerModifier(currentIsPlaying.value, player)
    AndroidView(
        factory = {
            PlayerView(it).apply {
                controllerShowTimeoutMs = 1000
            }
        },
        modifier = pipModifier.fillMaxSize()
    ) {
        log {
            "Video ${currentSession.uuid} update"
        }
        it.player = player
    }
    if (currentLoading.value && currentSession.contentType != M3U8_MIMETYPE) {
        CircularProgressIndicator(
            modifier = Modifier.Companion
                .align(Alignment.Center)
                .height(40.dp)
        )
    }
    CheckEnterPip(currentIsPlaying.value)
    PlayerBroadcastReceiver(player)
}

@Composable
private fun CheckEnterPip(currentIsPlaying: Boolean) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        DisposableEffect(context) {
            val onUserLeaveBehavior: () -> Unit = {
                if (currentIsPlaying) {
                    context.findActivity()
                        .enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                }
            }
            context.findActivity().addOnUserLeaveHintListener(
                onUserLeaveBehavior
            )
            onDispose {
                context.findActivity().removeOnUserLeaveHintListener(
                    onUserLeaveBehavior
                )
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun player(
    player: MediaController,
    currentSession: MediaPlayerSession
): Pair<MutableState<Boolean>, MutableState<Boolean>> {
    val currentLoading = remember {
        mutableStateOf(false)
    }
    val currentIsPlaying = remember {
        mutableStateOf(false)
    }
    val toasterState = LocalToaster.current
    val scope = rememberCoroutineScope()
    val l = buildListener(player, currentSession.id, toasterState, scope, object : VideoListener {
        override fun onPlayStateChange(isPlaying: Boolean) {
            currentIsPlaying.value = isPlaying
        }

        override fun onUpdateSize(size: CustomVideoSize) {
            Napier.d {
                "Video ${currentSession.uuid} updateSize $size"
            }
            MediaProvider.update(currentSession.copy(videoSize = size))
        }

        override fun onUpdateLoading(isLoading: Boolean) {
            currentLoading.value = isLoading
        }
    })
    DisposableEffect(currentSession, player) {
        player.addListener(l)
        onDispose {
            log {
                "Video ${currentSession.uuid} release"
            }
            player.removeListener(l)
        }
    }
    return Pair(currentLoading, currentIsPlaying)
}

@Composable
private fun buildPlayerModifier(
    currentIsPlaying: Boolean,
    player: MediaController
): Modifier {
    val context = LocalContext.current

    val pipModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.onGloballyPositioned { layoutCoordinates ->
            val sourceRect = layoutCoordinates.boundsInWindow().toAndroidRectF().toRect()
            val builder = PictureInPictureParams.Builder()
            // 12 之后引入
            builder.setSourceRectHint(sourceRect)
            builder.setAutoEnterEnabled(currentIsPlaying)
            builder.setActions(listOf())
            val rational = if (player.videoSize.height != 0 && player.videoSize.width != 0) {
                Rational(player.videoSize.width, player.videoSize.height)
            } else {
                Rational(16, 9)
            }
            builder.setAspectRatio(rational)
            context.findActivity().setPictureInPictureParams(builder.build())
        }
    } else {
        Modifier
    }
    return pipModifier
}

@Composable
private fun VideoOpRow(
    playList: List<PlayItem>,
    url: String,
    contentType: String,
    isActive: Boolean,
    closeSheet: () -> Unit
) {
    val context = LocalContext.current
    val toasterState = LocalToaster.current
    FlowRow {
        val clipboardManager = LocalClipboardManager.current

        if (playList.size > 1) {
            IconButton(closeSheet) {
                Icon(Icons.AutoMirrored.Default.List, "playlist")
            }
        }
        IconButton({
            clipboardManager.setText(buildAnnotatedString {
                append(url)
            })
            toasterState.show("copied", duration = 1.seconds)
        }) {
            Icon(Icons.Default.ContentCopy, "copy list")
        }
        if (contentType != M3U8_MIMETYPE) {
            val uriHandler = LocalUriHandler.current
            IconButton({
                uriHandler.openUri(url)
            }) {
                Icon(Icons.Default.Download, "download")
            }
        }
        if (isActive) {
            IconButton({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.findActivity().enterPictureInPictureMode(
                        PictureInPictureParams.Builder().build()
                    )
                } else {
                    toasterState.show("not support")
                }
            }) {
                Icon(Icons.Default.PictureInPicture, "pip")
            }
        }
    }
}

private fun MediaController.playNewMedia(
    playList: List<PlayItem>,
    contentType: String
) {
    clearMediaItems()
    addMediaItems(playList.map { playItem ->
        val url1 = playItem.url
        MediaItem.Builder().setUri(url1)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(playItem.icon?.let { Uri.parse(it) })
                    .setTitle(playItem.title)
                    .build()
            )
            .apply {
                if (contentType == M3U8_MIMETYPE && !url1.endsWith(".m3u8")) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }.build()
    })
    play()
}

private fun buildListener(
    player: Player,
    id: String,
    toasterState: ToasterState,
    scope: CoroutineScope,
    listener: VideoListener
): Player.Listener {
    return object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            listener.onUpdateSize(CustomVideoSize(videoSize.width, videoSize.height))
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Napier.i {
                "Video $id error $error ${error.errorCode} ${error.errorCodeName}"
            }
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ) {
                toasterState.show("source error, restart after 1 seconds")
                scope.launch {
                    delay(1000)
                    player.play()
                }
            } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) {
                toasterState.show("source error, skip to next after 1 seconds")
                scope.launch {
                    delay(1000)
                    player.seekToNext()
                    player.play()
                }
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            super.onIsLoadingChanged(isLoading)
            listener.onUpdateLoading(isLoading)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            listener.onPlayStateChange(isPlaying)
        }
    }
}

internal fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    error("Picture in picture should be called in the context of an Activity")
}

@Composable
actual fun rememberIsInPipMode(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = LocalContext.current.findActivity()
        var pipMode by remember { mutableStateOf(activity.isInPictureInPictureMode) }
        DisposableEffect(activity) {
            val observer = Consumer<PictureInPictureModeChangedInfo> { info ->
                pipMode = info.isInPictureInPictureMode
            }
            activity.addOnPictureInPictureModeChangedListener(
                observer
            )
            onDispose { activity.removeOnPictureInPictureModeChangedListener(observer) }
        }
        return pipMode
    } else {
        return false
    }
}

@Composable
fun PlayerBroadcastReceiver(player: Player) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val isInPipMode = rememberIsInPipMode()
        if (isInPipMode) {
            val context = LocalContext.current

            DisposableEffect(player) {
                val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if ((intent == null) || (intent.action != ACTION_BROADCAST_CONTROL)) {
                            return
                        }

                        when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                            EXTRA_CONTROL_PAUSE -> player.pause()
                            EXTRA_CONTROL_PLAY -> player.play()
                        }
                    }
                }
                ContextCompat.registerReceiver(
                    context,
                    broadcastReceiver,
                    IntentFilter(ACTION_BROADCAST_CONTROL),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                onDispose {
                    context.unregisterReceiver(broadcastReceiver)
                }
            }
        }
    }
}
