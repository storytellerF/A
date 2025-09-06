package com.storyteller_f.a.app.compose_app.compontents

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.storyteller_f.a.app.compose_app.LocalMediaPlaySession
import com.storyteller_f.a.app.compose_app.LocalToaster
import com.storyteller_f.a.app.compose_app.MediaPlayerActivity
import com.storyteller_f.a.app.compose_app.MultiMediaInfo
import com.storyteller_f.shared.commonJson
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

// Constant for broadcast receiver
const val ACTION_BROADCAST_CONTROL = "broadcast_control"

// Intent extras for broadcast controls from Picture-in-Picture mode.
const val EXTRA_CONTROL_TYPE = "control_type"
const val EXTRA_CONTROL_PLAY = 1
const val EXTRA_CONTROL_PAUSE = 2

@Composable
actual fun VideoView(
    obj: RemoteMediaItem,
    isEmbed: Boolean,
) {
    val contentType = obj.contentType
    MediaPlayerInternal(obj.url, isEmbed, contentType) { player, playingSession, currentSession ->
        VideoPlayer(playingSession, currentSession, player, isEmbed, obj)
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun VideoPlayer(
    playingSession: MultiMediaInfo.Player?,
    currentSession: LocalMediaPlaySession,
    player: MediaController,
    isEmbed: Boolean,
    obj: RemoteMediaItem,
) {
    val contentType = obj.contentType
    val ratio = remember(playingSession, currentSession, isEmbed) {
        if (playingSession?.videoSize != null && playingSession.uuids.lastOrNull() == currentSession.uuid && !isEmbed) {
            playingSession.videoSize.width.toFloat() / playingSession.videoSize.height
        } else {
            16f / 9
        }
    }
    Napier.d {
        "Video ${currentSession.uuid} ratio $ratio ${playingSession?.uuids} ${playingSession?.videoSize} $isEmbed"
    }
    Box(modifier = Modifier.aspectRatio(ratio)) {
        when {
            playingSession == null -> PlayerWaiting(currentSession, obj)
            playingSession.uuids.lastOrNull() == currentSession.uuid -> VideoPlayerInternal(
                currentSession,
                player,
                contentType
            )

            playingSession.id == currentSession.id -> PlayerOccupy(currentSession)
            else -> PlayerWaiting(currentSession, obj)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BoxScope.VideoPlayerInternal(
    currentSession: LocalMediaPlaySession,
    player: MediaController,
    contentType: String,
) {
    AndroidPlayerContainer(currentSession, player) { pipModifier, (currentIsLoading) ->
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
        if (currentIsLoading && contentType != M3U8_MIMETYPE) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(40.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BoxScope.AndroidPlayerContainer(
    currentSession: LocalMediaPlaySession,
    player: MediaController,
    block: @Composable BoxScope.(Modifier, MediaPlayerState) -> Unit,
) {
    val playerState = listenPlayerState(player, currentSession)
    val pipModifier = Modifier.buildPlayerModifier(playerState.currentIsPlaying, player)
    block(pipModifier, playerState)
    CheckEnterPipPre31(playerState.currentIsPlaying)
    PlayerBroadcastReceiver(player)
}

@Composable
private fun CheckEnterPipPre31(currentIsPlaying: Boolean) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        DisposableEffect(context) {
            val onUserLeaveBehavior = Runnable {
                if (currentIsPlaying) {
                    context.findActivity()
                        .enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                }
            }
            context.findActivity().addOnUserLeaveHintListener(onUserLeaveBehavior)
            onDispose {
                context.findActivity().removeOnUserLeaveHintListener(onUserLeaveBehavior)
            }
        }
    }
}

@Composable
private fun Modifier.buildPlayerModifier(
    currentIsPlaying: Boolean,
    player: MediaController,
): Modifier {
    log {
        "Video buildPlayerModifier $currentIsPlaying"
    }
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }
    return onGloballyPositioned { layoutCoordinates ->
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
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun VideoOrAudioOpRow(
    localMediaPlaySession: LocalMediaPlaySession,
    playingSession: MultiMediaInfo.Player?,
    contentType: String,
    showSheet: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toasterState = LocalToaster.current
    FlowRow {
        val clipboardManager = LocalClipboard.current
        val isActive = localMediaPlaySession.uuid == playingSession?.uuids?.lastOrNull()

        IconButton(showSheet, enabled = isActive) {
            Icon(Icons.AutoMirrored.Default.List, "playlist")
        }
        IconButton({
            scope.launch {
                clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("text", localMediaPlaySession.id)))
                toasterState.showMessage("copied")
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
        IconButton({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.findActivity().enterPictureInPictureMode(
                    PictureInPictureParams.Builder().build()
                )
            } else {
                toasterState.showMessage("not support")
            }
        }, enabled = isActive) {
            Icon(Icons.Default.PictureInPicture, "pip")
        }
        IconButton({
            if (localMediaPlaySession.uuid == playingSession?.uuids?.lastOrNull()) {
                context.startActivity(Intent(context, MediaPlayerActivity::class.java).apply {
                    putExtra("json", commonJson.encodeToString<MultiMediaInfo>(playingSession))
                })
            }
        }, enabled = isActive) {
            Icon(Icons.Default.Fullscreen, "fullscreen")
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
            activity.addOnPictureInPictureModeChangedListener(observer)
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
