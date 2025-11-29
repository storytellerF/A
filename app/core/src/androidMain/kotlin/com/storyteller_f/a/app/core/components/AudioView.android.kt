package com.storyteller_f.a.app.core.components

import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.storyteller_f.shared.model.FileInfo
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi

@Composable
actual fun AudioViewEmbed(remoteMediaItem: RemoteMediaItem) {
    MediaPlayerEmbed(
        remoteMediaItem,
        { playingSession, localMediaPlaySession ->
            AudioPlayer(playingSession, localMediaPlaySession, remoteMediaItem)
        }
    )
}

@Composable
actual fun AudioViewFilled(remoteMediaItem: RemoteMediaItem) {
    MediaPlayerFilled(
        remoteMediaItem,
        { playingSession, localMediaPlaySession ->
            AudioPlayer(playingSession, localMediaPlaySession, remoteMediaItem)
        }
    )
}

@Composable
actual fun AudioViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    MediaPlayerFullScreen(
        remoteMediaItem,
        { playingSession, localMediaPlaySession ->
            AudioPlayer(playingSession, localMediaPlaySession, remoteMediaItem)
        }
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun AudioPlayer(
    playingSession: MediaPlaySession?,
    localMediaPlaySession: LocalMediaPlaySession,
    remoteMediaItem: RemoteMediaItem
) {
    val mediaPlayerService = LocalMediaPlayerService.current
    val player by mediaPlayerService.controller.collectAsState(null)
    val playerState by rememberPlayerState(player, localMediaPlaySession)
    val enablePip = playerState.currentIsPlaying && (playingSession?.lastUuid == localMediaPlaySession.uuid)
    Napier.d(tag = "MediaPlayer") {
        "VideoPlayer ${localMediaPlaySession.uuid} enablePip: $enablePip"
    }
    val ratio = Rational(16, 9)
    val pipModifier = Modifier.androidPipMode(enablePip, ratio)
    Box(modifier = pipModifier.aspectRatio(ratio.toFloat())) {
        when {
            player == null -> PlayerWaiting(localMediaPlaySession, remoteMediaItem)
            playingSession == null -> PlayerWaiting(localMediaPlaySession, remoteMediaItem)
            playingSession.lastUuid == localMediaPlaySession.uuid -> AudioPlayerInternal(
                player,
                localMediaPlaySession,
                remoteMediaItem,
                playingSession
            )

            playingSession.id == localMediaPlaySession.id -> PlayerOccupy(localMediaPlaySession)
            else -> PlayerWaiting(localMediaPlaySession, remoteMediaItem)
        }
    }
}

@Composable
private fun BoxScope.AudioPlayerInternal(
    player: MediaController?,
    localMediaPlaySession: LocalMediaPlaySession,
    remoteMediaItem: RemoteMediaItem,
    playingSession: MediaPlaySession
) {
    player ?: return
    AndroidPlayerContainer(localMediaPlaySession, player) {
        val coverMediaInfo = remoteMediaItem.cover
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioCover(coverMediaInfo)
            AudioDetail(player, localMediaPlaySession, playingSession)
        }
    }
}

@Composable
private fun RowScope.AudioDetail(
    player: MediaController,
    localMediaPlaySession: LocalMediaPlaySession,
    playingSession: MediaPlaySession
) {
    Box(
        modifier = Modifier
            .weight(2f)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            val state by rememberPlayerState(player, localMediaPlaySession)
            if (state.currentLoading) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            } else {
                AudioTitle(state, playingSession)
                AudioPlayerControls(player, state)
            }
        }
    }
}

@Composable
private fun AudioTitle(
    state: MediaPlayerState,
    playingSession: MediaPlaySession
) {
    val title = state.currentPlayingItem?.mediaMetadata?.title?.toString()
            ?: playingSession.remoteMediaItem.title
            ?: playingSession.remoteMediaItem.name
    Text(title, maxLines = 2, modifier = Modifier.basicMarquee().padding(horizontal = 20.dp))
}

@Composable
private fun RowScope.AudioCover(coverMediaInfo: FileInfo?) {
    val request = coverMediaInfo?.let { imageRequestInMarkdown(it) }
    val modifier = Modifier
        .aspectRatio(1f)
        .clip(CircleShape)
        .weight(1f)
        .widthIn(max = 100.dp)
    if (request == null) {
        Icon(Icons.Default.Audiotrack, "cover", modifier)
    } else {
        AsyncImage(
            request,
            contentDescription = "cover",
            modifier = modifier,
            fallback = rememberVectorPainter(Icons.Default.Audiotrack)
        )
    }
}

@Composable
private fun AudioPlayerControls(
    player: MediaController,
    state: MediaPlayerState
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        IconButton({
            if (player.isPlaying) {
                player.pause()
            } else if (!player.isLoading) {
                player.play()
            }
        }) {
            when {
                state.currentIsPlaying -> Icon(Icons.Default.PauseCircle, "pause", modifier = Modifier.size(40.dp))

                else -> Icon(Icons.Default.PlayCircle, "play", modifier = Modifier.size(40.dp))
            }
        }
    }
}
