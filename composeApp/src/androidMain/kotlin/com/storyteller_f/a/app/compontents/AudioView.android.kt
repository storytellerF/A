package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.LocalMediaPlaySession
import com.storyteller_f.a.app.MediaPlaySession
import com.storyteller_f.a.app.common.CenterBox
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
actual fun AudioView(obj: RemoteMediaItem, isEmbed: Boolean) {
    val url = obj.url
    MediaPlayerInternal(url, isEmbed) { player, playingSession, currentSession ->
        ObjectBlock(150.dp) {
            Box(modifier = Modifier.weight(1f).aspectRatio(16f / 9)) {
                when {
                    playingSession == null -> PlayerWaiting(currentSession, obj)
                    playingSession.uuid == currentSession.uuid -> AudioPlayer(
                        player,
                        currentSession,
                        obj,
                        playingSession
                    )

                    playingSession.id == currentSession.id -> PlayerOccupy(currentSession)
                    else -> PlayerWaiting(currentSession, obj)
                }
            }
        }
    }
}

@Composable
private fun AudioPlayer(
    player: MediaController,
    currentSession: LocalMediaPlaySession,
    obj: RemoteMediaItem,
    playingSession: MediaPlaySession.VideoOrAudio
) {
    val (currentIsLoading, currentIsPlaying, currentPlaying) = listenPlayerState(player, currentSession)
    val coverMediaInfo = obj.coverMediaInfo
    Row(
        modifier = Modifier.padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val request = coverMediaInfo?.let { imageRequestInMarkdown(it) }
        AsyncImage(
            request,
            contentDescription = "cover",
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(CircleShape),
            fallback = rememberVectorPainter(Icons.Default.Audiotrack)
        )
        CenterBox {
            Column {
                if (currentIsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                } else {
                    val title =
                        currentPlaying?.mediaMetadata?.title?.toString() ?: playingSession.obj.title
                            ?: playingSession.obj.name
                    Text(title, maxLines = 2, modifier = Modifier.basicMarquee().padding(horizontal = 20.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        IconButton({
                            if (player.isPlaying) {
                                player.pause()
                            } else if (!player.isLoading) {
                                player.play()
                            }
                        }) {
                            when {
                                currentIsPlaying -> Icon(
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
    }
}
