package com.storyteller_f.a.app.core.components

import android.app.PictureInPictureParams
import android.content.ClipData
import android.util.Rational
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.storyteller_f.shared.model.FileInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

// Constant for broadcast receiver
const val ACTION_BROADCAST_CONTROL = "broadcast_control"

// Intent extras for broadcast controls from Picture-in-Picture mode.
const val EXTRA_CONTROL_TYPE = "control_type"
const val EXTRA_CONTROL_PLAY = 1
const val EXTRA_CONTROL_PAUSE = 2

@Composable
actual fun VideoViewEmbed(remoteMediaItem: RemoteMediaItem) {
    MediaPlayerEmbed(
        remoteMediaItem,
        { playingSession, localMediaPlaySession ->
            VideoPlayer(playingSession, localMediaPlaySession, remoteMediaItem)
        }
    )
}

@Composable
actual fun VideoViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    MediaPlayerFullScreen(
        remoteMediaItem,
        { playingSession, localMediaPlaySession ->
            VideoPlayer(playingSession, localMediaPlaySession, remoteMediaItem)
        }
    )
}

@Composable
actual fun VideoViewFilled(remoteMediaItem: RemoteMediaItem) {
    MediaPlayerFilled(
        remoteMediaItem,
        { playingSession, localMediaPlaySession ->
            VideoPlayer(playingSession, localMediaPlaySession, remoteMediaItem)
        }
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun VideoPlayer(
    playingSession: MediaPlaySession?,
    localMediaPlaySession: LocalMediaPlaySession,
    remoteMediaItem: RemoteMediaItem,
) {
    val mediaPlayerService = LocalMediaPlayerService.current
    val player by mediaPlayerService.controller.collectAsState()
    val contentType = remoteMediaItem.contentType
    val videoSize = playingSession?.videoSize
    val ratio = remember(playingSession, localMediaPlaySession) {
        if (videoSize != null &&
            playingSession.lastUuid == localMediaPlaySession.uuid
        ) {
            Rational(videoSize.width, videoSize.height)
        } else {
            Rational(16, 9)
        }
    }
    Napier.d {
        "VideoPlayer ${localMediaPlaySession.uuid} ratio $ratio ${playingSession?.uuids} $videoSize"
    }
    val playerState by rememberPlayerState(
        player,
        localMediaPlaySession
    )
    val enablePip =
        playerState.currentIsPlaying && (playingSession?.lastUuid == localMediaPlaySession.uuid)
    Napier.d(tag = "MediaPlayer") {
        "VideoPlayer ${localMediaPlaySession.uuid} $enablePip"
    }
    val pipModifier = Modifier.androidPipMode(enablePip, ratio)
    EnablePipPre31(enablePip, localMediaPlaySession)
    Box(modifier = pipModifier.aspectRatio(ratio.toFloat())) {
        when {
            player == null -> PlayerWaiting(localMediaPlaySession, remoteMediaItem)
            playingSession == null -> PlayerWaiting(localMediaPlaySession, remoteMediaItem)
            playingSession.lastUuid == localMediaPlaySession.uuid -> VideoPlayerInternal(
                localMediaPlaySession,
                player,
                contentType
            )

            playingSession.id == localMediaPlaySession.id -> PlayerOccupy(localMediaPlaySession)
            else -> PlayerWaiting(localMediaPlaySession, remoteMediaItem)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BoxScope.VideoPlayerInternal(
    localMediaPlaySession: LocalMediaPlaySession,
    player: MediaController?,
    contentType: String,
) {
    player ?: return
    AndroidPlayerContainer(localMediaPlaySession, player) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    controllerShowTimeoutMs = 1000
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Napier.i {
                "Video ${localMediaPlaySession.uuid} update"
            }
            it.player = player
        }
        val playerState by rememberPlayerState(
            player,
            localMediaPlaySession
        )
        if (playerState.currentIsPlaying && contentType != FileInfo.M3U8_MIMETYPE) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(40.dp)
            )
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun EmbedMediaPlayerMenus(
    localMediaPlaySession: LocalMediaPlaySession,
    playingSession: MediaPlaySession?,
    contentType: String,
    showSheet: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toasterState = LocalToaster.current
    FlowRow {
        val clipboardManager = LocalClipboard.current
        val isActive = localMediaPlaySession.uuid == playingSession?.lastUuid

        IconButton(showSheet, enabled = isActive) {
            Icon(Icons.AutoMirrored.Default.List, "playlist")
        }
        IconButton({
            scope.launch {
                clipboardManager.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText(
                            "text",
                            localMediaPlaySession.id
                        )
                    )
                )
                toasterState.showMessage("copied")
            }
        }) {
            Icon(Icons.Default.ContentCopy, "copy list")
        }
        if (contentType != FileInfo.M3U8_MIMETYPE) {
            val uriHandler = LocalUriHandler.current
            IconButton({
                uriHandler.openUri(localMediaPlaySession.id)
            }) {
                Icon(Icons.Default.Download, "download")
            }
        }
        IconButton({
            context.findActivity().enterPictureInPictureMode(
                PictureInPictureParams.Builder().build()
            )
        }, enabled = isActive) {
            Icon(Icons.Default.PictureInPicture, "pip")
        }
        val mediaPlayerService = LocalMediaPlayerService.current
        IconButton({
            if (localMediaPlaySession.uuid == playingSession?.lastUuid) {
                mediaPlayerService.fullscreen(playingSession.remoteMediaItem)
            }
        }, enabled = isActive) {
            Icon(Icons.Default.Fullscreen, "fullscreen")
        }
    }
}

@Composable
actual fun rememberIsInPipMode(): Boolean {
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
}
