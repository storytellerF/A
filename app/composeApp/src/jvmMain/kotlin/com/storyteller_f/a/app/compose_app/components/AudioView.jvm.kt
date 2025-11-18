package com.storyteller_f.a.app.compose_app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import io.github.aakira.napier.Napier
import javazoom.jl.player.FactoryRegistry
import javazoom.jl.player.JavaSoundAudioDeviceFactory
import javazoom.jl.player.jlp

@Composable
actual fun AudioViewEmbed(remoteMediaItem: RemoteMediaItem) {
    AudioPlayerInternal(remoteMediaItem.url) {
        AudioPlayer(it)
    }
}

@Composable
actual fun AudioViewFilled(remoteMediaItem: RemoteMediaItem) = Unit

@Composable
actual fun AudioViewFullScreen(remoteMediaItem: RemoteMediaItem) = Unit

@Composable
fun AudioPlayerInternal(url: String, block: @Composable (AudioPlayerComponent) -> Unit) {
    val component = remember {
        AudioPlayerComponent(url)
    }
    block(component)
}

@Composable
fun AudioPlayer(component: AudioPlayerComponent) {
    val currentPlaying by component.isPlaying
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(100.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        IconButton(
            {
                if (currentPlaying) {
                    component.stop()
                } else {
                    component.play()
                }
            },
            enabled = component.player != null,
        ) {
            when {
                currentPlaying -> Icon(
                    Icons.Default.PauseCircle,
                    "pause",
                    modifier = Modifier.size(40.dp)
                )

                else -> Icon(Icons.Default.PlayCircle, "play", modifier = Modifier.size(40.dp))
            }
        }
    }
}

class AudioPlayerComponent(url: String) {
    val isPlaying = mutableStateOf(false)
    val player: jlp? = createPlayer(url)

    fun play() {
        isPlaying.value = true
        player?.play()
    }

    fun stop() {
        isPlaying.value = false
        player?.stop()
    }
}

private fun createPlayer(url: String): jlp? = try {
    jlp.createInstance(arrayOf("-url", url)).apply {
        setAudioDevice(
            FactoryRegistry.systemRegistry()
                .createAudioDevice(JavaSoundAudioDeviceFactory::class.java)
        )
    }
} catch (e: Exception) {
    Napier.e(e) {
        "AudioView"
    }
    null
}
