package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import java.util.Locale

@Composable
actual fun VideoViewEmbed(remoteMediaItem: RemoteMediaItem) {
    VideoPlayerInternal { mediaPlayerComponent, mediaPlayerState ->
        val controls = mediaPlayerComponent.mediaPlayer().controls()
        MediaObjectBlock {
            Box(Modifier.weight(1f)) {
                VideoPlayer(mediaPlayerComponent)
            }
            Row(modifier = Modifier) {
                if (mediaPlayerState.currentIsPlaying) {
                    IconButton({
                        controls.pause()
                    }) {
                        Icon(Icons.Default.PauseCircle, "pause")
                    }
                } else {
                    IconButton({
                        controls.play()
                    }) {
                        Icon(Icons.Default.PlayCircle, "play")
                    }
                }
            }
        }
    }
}

@Composable
actual fun VideoViewFullScreen(remoteMediaItem: RemoteMediaItem) = Unit

@Composable
actual fun VideoViewFilled(remoteMediaItem: RemoteMediaItem) = Unit

@Composable
fun VideoPlayer(mediaPlayerComponent: Component) {
    SwingPanel(
        factory = {
            mediaPlayerComponent
        },
        modifier = Modifier.aspectRatio(16f / 9)
    ) {
    }
}

@Composable
fun VideoPlayerInternal(block: @Composable (Component, VideoMediaPlayerState) -> Unit) {
    val mediaPlayerComponent = remember {
        initializeMediaPlayerComponent()
    }
    DisposableEffect(mediaPlayerComponent) {
        onDispose {
            mediaPlayerComponent.release()
        }
    }
    val mediaPlayerState by rememberMediaPlayerState(mediaPlayerComponent)
    block(mediaPlayerComponent, mediaPlayerState)
}

data class VideoMediaPlayerState(
    val currentLoading: Boolean,
    val currentIsPlaying: Boolean,
)

@Composable
fun rememberMediaPlayerState(mediaPlayerComponent: Component): State<VideoMediaPlayerState> {
    var isPlaying by remember {
        mutableStateOf(false)
    }
    var isBuffering by remember {
        mutableStateOf(false)
    }
    DisposableEffect(mediaPlayerComponent) {
        val listener = object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                super.playing(mediaPlayer)
                isPlaying = true
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                super.paused(mediaPlayer)
                isPlaying = false
            }

            override fun error(mediaPlayer: MediaPlayer?) {
                super.error(mediaPlayer)
                isBuffering = false
            }

            override fun buffering(mediaPlayer: MediaPlayer?, newCache: Float) {
                super.buffering(mediaPlayer, newCache)
            }
        }
        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(listener)
        onDispose {
            mediaPlayerComponent.mediaPlayer().events().removeMediaPlayerEventListener(listener)
        }
    }
    return remember {
        derivedStateOf {
            VideoMediaPlayerState(currentLoading = isBuffering, currentIsPlaying = isPlaying)
        }
    }
}

fun initializeMediaPlayerComponent(): Component {
    NativeDiscovery().discover()
    val component: Component = if (isMacOS()) {
        CallbackMediaPlayerComponent()
    } else {
        EmbeddedMediaPlayerComponent()
    }
    return component
}

/**
 * Returns [MediaPlayer] from player components.
 * The method names are the same, but they don't share the same parent/interface.
 * That's why we need this method.
 */
fun Component.mediaPlayer(): EmbeddedMediaPlayer = when (this) {
    is CallbackMediaPlayerComponent -> mediaPlayer()
    is EmbeddedMediaPlayerComponent -> mediaPlayer()
    else -> error("mediaPlayer() can only be called on vlcj player components")
}

fun Component.release() {
    when (this) {
        is CallbackMediaPlayerComponent -> release()
        is EmbeddedMediaPlayerComponent -> release()
        else -> error("mediaPlayer() can only be called on vlcj player components")
    }
}

private fun isMacOS(): Boolean {
    val os = System
        .getProperty("os.name", "generic")
        .lowercase(Locale.ENGLISH)
    return "mac" in os || "darwin" in os
}

@Composable
actual fun rememberIsInPipMode(): Boolean {
    return false
}
