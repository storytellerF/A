package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import com.storyteller_f.a.app.LocalMediaPlaySession
import com.storyteller_f.a.app.common.CenterBox
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.utils.MarkdownObject
import io.github.aakira.napier.log
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
actual fun AudioView(obj: RemoteMediaItem, coverInfo: MediaInfo?) {
    val url = obj.url
    val contentType = obj.contentType
    MediaPlayerInternal(url) { player, playingSession, currentSession ->
        ObjectBlock {
            Box {
                when {
                    playingSession == null -> PlayerWaiting(currentSession, null, MarkdownObject(contentType, url))
                    playingSession.uuid == currentSession.uuid -> AudioPlayer(player, currentSession)
                    playingSession.id == currentSession.id -> PlayerOccupy(currentSession)
                    else -> PlayerWaiting(currentSession, null, MarkdownObject(contentType, url))
                }
            }
        }
    }
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
}

@Composable
private fun AudioPlayer(
    player: MediaController,
    currentSession: LocalMediaPlaySession
) {
    val (currentIsLoading, currentIsPlaying) = listenPlayerState(player, currentSession)

    Row(
        modifier = Modifier.padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(60.dp))
        CenterBox {
            if (currentIsLoading.value) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            } else {
                IconButton({
                    if (player.isPlaying) {
                        player.pause()
                    } else if (!player.isLoading) {
                        player.play()
                    }
                }) {
                    when {
                        currentIsPlaying.value -> Icon(
                            Icons.Default.PauseCircle,
                            "pause",
                            modifier = Modifier.size(40.dp)
                        )

                        else -> Icon(
                            Icons.Default.PlayCircle,
                            "play",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}
