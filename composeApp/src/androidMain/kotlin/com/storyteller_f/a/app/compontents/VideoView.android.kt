package com.storyteller_f.a.app.compontents

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun VideoView(modifier: Modifier, url: String, contentType: String) {
    log {
        "Video $url"
    }
    val toasterState = rememberToasterState()
    Toaster(toasterState, alignment = Alignment.Center)
    val context = LocalContext.current
    var size by remember {
        mutableStateOf<VideoSize?>(null)
    }
    val scope = rememberCoroutineScope()
    var currentLoading by remember {
        mutableStateOf(false)
    }
    val player = remember {
        rememberMediaPlayer(url, context, toasterState, scope, contentType, {
            size = it
        }) {
            currentLoading = it
        }
    }
    DisposableEffect(player) {
        onDispose {
            log {
                "Video $url release"
            }
            player.release()
        }
    }

    val shape = RoundedCornerShape(20.dp)
    Box(modifier = modifier) {
        AndroidView(
            factory = {
                PlayerView(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9)
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .clip(shape)
        ) {
            log {
                "Video $url update"
            }
            it.player = player
        }
        if (currentLoading && contentType != "application/vnd.apple.mpegurl") {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(40.dp)
            )
        }
    }
}

private fun rememberMediaPlayer(
    url: String,
    context: Context,
    toasterState: ToasterState,
    scope: CoroutineScope,
    contentType: String,
    updateSize: (VideoSize) -> Unit,
    updateLoading: (Boolean) -> Unit,
): ExoPlayer {
    log {
        "Video $url init"
    }
    return ExoPlayer.Builder(context).build().apply {
        addListener(buildListener(url, toasterState, scope, updateSize, updateLoading))

        addMediaItem(MediaItem.Builder().setUri(url).apply {
            if (contentType == "application/vnd.apple.mpegurl" && !url.endsWith(".m3u8")) {
                setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }.build())
        prepare()
    }
}

private fun ExoPlayer.buildListener(
    url: String,
    toasterState: ToasterState,
    scope: CoroutineScope,
    updateSize: (VideoSize) -> Unit,
    updateLoading: (Boolean) -> Unit,
): Player.Listener {
    return object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            updateSize(videoSize)
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
            updateLoading(isLoading)
        }
    }
}
