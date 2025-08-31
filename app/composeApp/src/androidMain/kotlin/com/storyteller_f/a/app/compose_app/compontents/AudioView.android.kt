package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.storyteller_f.a.app.compose_app.LocalMediaPlaySession
import com.storyteller_f.a.app.compose_app.MultiMediaInfo
import kotlin.uuid.ExperimentalUuidApi

@Composable
actual fun AudioView(obj: RemoteMediaItem, isEmbed: Boolean) {
    MediaPlayerInternal(obj.url, isEmbed, obj.contentType) { player, playingSession, currentSession ->
        AudioPlayer(playingSession, currentSession, obj, player)
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun AudioPlayer(
    playingSession: MultiMediaInfo.Player?,
    currentSession: LocalMediaPlaySession,
    obj: RemoteMediaItem,
    player: MediaController
) {
    Box(modifier = Modifier.aspectRatio(16f / 9)) {
        when {
            playingSession == null -> PlayerWaiting(currentSession, obj)
            playingSession.uuids.lastOrNull() == currentSession.uuid -> AudioPlayer(
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

@Composable
private fun BoxScope.AudioPlayer(
    player: MediaController,
    currentSession: LocalMediaPlaySession,
    obj: RemoteMediaItem,
    playingSession: MultiMediaInfo.Player
) {
    AndroidPlayerContainer(currentSession, player) { pipModifier, state ->
        val coverMediaInfo = obj.cover
        Row(
            modifier = pipModifier.padding(10.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val request = coverMediaInfo?.let { imageRequestInMarkdown(it) }
            AsyncImage(
                request,
                contentDescription = "cover",
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .weight(1f)
                    .widthIn(max = 100.dp),
                fallback = rememberVectorPainter(Icons.Default.Audiotrack)
            )
            Box(
                modifier = Modifier
                    .weight(2f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AudioPlayerInternal(state, playingSession, player)
            }
        }
    }
}

@Composable
private fun AudioPlayerInternal(
    state: MediaPlayerState,
    playingSession: MultiMediaInfo.Player,
    player: MediaController
) {
    Column(modifier = Modifier.padding(10.dp)) {
        if (state.currentLoading) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        } else {
            val title =
                state.currentPlayingItem?.mediaMetadata?.title?.toString() ?: playingSession.obj.title
                    ?: playingSession.obj.name
            Text(
                title,
                maxLines = 2,
                modifier = Modifier
                    .basicMarquee()
                    .padding(horizontal = 20.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton({
                    if (player.isPlaying) {
                        player.pause()
                    } else if (!player.isLoading) {
                        player.play()
                    }
                }) {
                    when {
                        state.currentIsPlaying -> Icon(
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
