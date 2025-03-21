package com.storyteller_f.a.app.compontents

import android.app.PictureInPictureParams
import android.content.*
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.util.Consumer
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.storyteller_f.a.app.LocalMediaPlaySession
import com.storyteller_f.a.app.LocalToaster
import com.storyteller_f.a.app.MediaPlaySession
import com.storyteller_f.a.app.MediaPlayerActivity
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.utils.MarkdownObject
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

// Constant for broadcast receiver
const val ACTION_BROADCAST_CONTROL = "broadcast_control"

// Intent extras for broadcast controls from Picture-in-Picture mode.
const val EXTRA_CONTROL_TYPE = "control_type"
const val EXTRA_CONTROL_PLAY = 1
const val EXTRA_CONTROL_PAUSE = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
actual fun VideoView(
    obj: RemoteMediaItem,
    coverMediaInfo: MediaInfo?,
    isEmbed: Boolean,
) {
    val contentType = obj.contentType
    MediaPlayerInternal(obj.url) { player, playingSession, currentSession ->
        var showSheet by remember {
            mutableStateOf(false)
        }
        if (isEmbed) {
            ObjectBlock {
                VideoPlayer(playingSession, currentSession, player, coverMediaInfo, true, obj)
                VideoOpRow(currentSession, playingSession, contentType) {
                    showSheet = false
                }
            }
        } else {
            VideoPlayer(playingSession, currentSession, player, coverMediaInfo, false, obj)
        }
        val sheetState = rememberModalBottomSheetState()
        if (playingSession?.uuid == currentSession.uuid) {
            VideoPlaylistPicker(showSheet, sheetState, {
                showSheet = false
            }, playingSession.playList) { _, i ->
                player.seekTo(i, 0)
                player.play()
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun VideoPlayer(
    playingSession: MediaPlaySession.Video?,
    currentSession: LocalMediaPlaySession,
    player: MediaController,
    coverMediaInfo: MediaInfo?,
    isEmbed: Boolean,
    obj: RemoteMediaItem
) {
    val contentType = obj.contentType
    val ratio = if (playingSession?.videoSize != null && playingSession.uuid == currentSession.uuid && !isEmbed) {
        playingSession.videoSize.width.toFloat() / playingSession.videoSize.height
    } else {
        16f / 9
    }
    Napier.d {
        "Video ${currentSession.uuid} ratio $ratio ${playingSession?.uuid} ${playingSession?.videoSize} $isEmbed"
    }
    Box(modifier = Modifier.aspectRatio(ratio)) {
        when {
            playingSession == null -> PlayerWaiting(currentSession, coverMediaInfo, obj)
            playingSession.uuid == currentSession.uuid -> AndroidPlayer(currentSession, player, contentType)
            playingSession.id == currentSession.id -> PlayerOccupy(currentSession)
            else -> PlayerWaiting(currentSession, coverMediaInfo, obj)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun BoxScope.AndroidPlayer(
    currentSession: LocalMediaPlaySession,
    player: MediaController,
    contentType: String
) {
    val (currentIsLoading, currentIsPlaying) = listenPlayerState(player, currentSession)
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
    if (currentIsLoading.value && contentType != M3U8_MIMETYPE) {
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

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun VideoOpRow(
    localMediaPlaySession: LocalMediaPlaySession,
    playingSession: MediaPlaySession.Video?,
    contentType: String,
    closeSheet: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toasterState = LocalToaster.current
    FlowRow {
        val clipboardManager = LocalClipboard.current

        if (localMediaPlaySession.uuid == playingSession?.uuid) {
            if (playingSession.playList.size > 1) {
                IconButton(closeSheet) {
                    Icon(Icons.AutoMirrored.Default.List, "playlist")
                }
            }
        }
        IconButton({
            scope.launch {
                clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("text", localMediaPlaySession.id)))
                toasterState.show("copied", duration = 1.seconds)
            }
        }) {
            Icon(Icons.Default.ContentCopy, "copy list")
        }
        if (contentType != M3U8_MIMETYPE) {
            val uriHandler = LocalUriHandler.current
            IconButton({
                uriHandler.openUri(localMediaPlaySession.id)
            }) {
                Icon(Icons.Default.Download, "download")
            }
        }
        if (localMediaPlaySession.uuid == playingSession?.uuid) {
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
            IconButton({
                context.startActivity(Intent(context, MediaPlayerActivity::class.java).apply {
                    putExtra("json", Json.encodeToString<MediaPlaySession>(playingSession))
                })
            }) {
                Icon(Icons.Default.Fullscreen, "fullscreen")
            }
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
