package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun VideoView(modifier: Modifier, url: String, contentType: String, playList: List<PlayItem>) {
    log {
        "Video $url"
    }
    var currentLoading by remember {
        mutableStateOf(false)
    }
    val player = rememberMediaPlayer(url, contentType, playList, object : VideoListener {
        override fun onUpdateSize(size: CustomVideoSize) = Unit

        override fun onUpdateLoading(isLoading: Boolean) {
            currentLoading = isLoading
        }
    })

    DisposableEffect(player) {
        onDispose {
            log {
                "Video $url release"
            }
            player.release()
        }
    }

    var showSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState()
    VideoViewInternal(currentLoading, contentType, url, player, {
        showSheet = true
    }.takeIf { playList.size > 1 })
    VideoPlaylistPicker(showSheet, sheetState, {
        showSheet = false
    }, playList) { e, i ->
        player.seekTo(i, 0)
        player.play()
    }
}

@Composable
fun VideoViewInternal(
    currentLoading: Boolean,
    contentType: String,
    url: String,
    player: ExoPlayer,
    showSheet1: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
    ) {
        Box(modifier = Modifier) {
            AndroidView(
                factory = {
                    PlayerView(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9)
            ) {
                log {
                    "Video $url update"
                }
                it.player = player
            }
            if (currentLoading && contentType != M3U8_MIMETYPE) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                )
            }
        }
        OpRow(showSheet1, contentType, url)
    }
}

@Composable
private fun rememberMediaPlayer(
    url: String,
    contentType: String,
    playList: List<PlayItem>,
    listener: VideoListener,
): ExoPlayer {
    log {
        "Video $url rememberMediaPlayer"
    }
    val context = LocalContext.current
    val toasterState = rememberToasterState()
    val scope = rememberCoroutineScope()
    return remember(playList, contentType) {
        ExoPlayer.Builder(context).build().apply {
            addListener(buildListener(url, toasterState, scope, listener))

            addMediaItems(playList.map {
                val url1 = it.url
                MediaItem.Builder().setUri(url1).apply {
                    if (contentType == M3U8_MIMETYPE && !url1.endsWith(".m3u8")) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }.build()
            })
            prepare()
        }
    }
}

private fun ExoPlayer.buildListener(
    url: String,
    toasterState: ToasterState,
    scope: CoroutineScope,
    listener: VideoListener,
): Player.Listener {
    return object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            listener.onUpdateSize(CustomVideoSize(videoSize.width, videoSize.height))
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Napier.i {
                "Video $url error $error ${error.errorCode} ${error.errorCodeName}"
            }
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                if (error is ExoPlaybackException) {
                    toasterState.show("source error, restart after 5 seconds")
                    scope.launch {
                        delay(5000)
                        prepare()
                        play()
                    }
                }
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            super.onIsLoadingChanged(isLoading)
            listener.onUpdateLoading(isLoading)
        }
    }
}
