package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import io.github.aakira.napier.log

@Composable
actual fun AudioView(url: String) {
    log {
        "Audio $url"
    }
    val context = LocalContext.current
    var size by remember {
        mutableStateOf<VideoSize?>(null)
    }
    var currentPlaying by remember {
        mutableStateOf(false)
    }
    var currentLoading by remember {
        mutableStateOf(false)
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    size = videoSize
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    log {
                        "Audio $url isPlaying $isPlaying"
                    }
                    super.onIsPlayingChanged(isPlaying)
                    currentPlaying = isPlaying
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    log {
                        "Audio $url isLoading $isLoading"
                    }
                    super.onIsLoadingChanged(isLoading)
                    currentLoading = isLoading
                }
            })
            addMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.pause()
        }
    }
    val shape = RoundedCornerShape(20.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(100.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
    ) {
        IconButton({
            if (player.isPlaying) {
                player.pause()
            } else if (!player.isLoading) {
                player.play()
            }
        }) {
            when {
                currentPlaying -> Icon(Icons.Default.PauseCircle, "pause", modifier = Modifier.size(40.dp))
                currentLoading -> Icon(Icons.Default.Pending, "loading", modifier = Modifier.size(40.dp))
                else -> Icon(Icons.Default.PlayCircle, "play", modifier = Modifier.size(40.dp))
            }
        }
    }
}
