package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.aakira.napier.log

@Composable
actual fun VideoView(url: String) {
    log {
        "Video $url"
    }
    val context = LocalContext.current
    var size by remember {
        mutableStateOf<VideoSize?>(null)
    }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    size = videoSize
                }
            })
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.pause()
        }
    }
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
        player.addMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }
}
