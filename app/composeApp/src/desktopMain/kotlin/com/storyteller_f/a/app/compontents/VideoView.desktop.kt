package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aakira.napier.Napier
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Component
import java.util.*

@Composable
actual fun VideoView(
    obj: RemoteMediaItem,
    isEmbed: Boolean
) {
    val id = obj.url
    var isPlaying by remember {
        mutableStateOf(false)
    }
    val mediaPlayerComponent = remember {
        initializeMediaPlayerComponent(object : MediaPlayerEventAdapter() {
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
                Napier.i {
                    "Video $id error"
                }
            }
        }).apply {
            mediaPlayer().media().prepare(id)
        }
    }
    val mediaPlayer = mediaPlayerComponent.mediaPlayer()
    val controls = mediaPlayer.controls()
    DisposableEffect(mediaPlayerComponent) {
        onDispose {
            mediaPlayerComponent.release()
        }
    }
    val shape = RoundedCornerShape(20.dp)
    ObjectBlock {
        SwingPanel(
            factory = {
                mediaPlayerComponent
            },
            modifier = Modifier.weight(1f)
                .aspectRatio(16f / 9).clip(shape)
        ) {
        }
        Row(modifier = Modifier) {
            if (isPlaying) {
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

fun initializeMediaPlayerComponent(listener: MediaPlayerEventListener): Component {
    NativeDiscovery().discover()
    val component: Component = if (isMacOS()) {
        CallbackMediaPlayerComponent()
    } else {
        EmbeddedMediaPlayerComponent()
    }
    component.mediaPlayer().events().addMediaPlayerEventListener(listener)
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
