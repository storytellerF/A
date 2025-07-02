package com.storyteller_f.a.app.compose_app.compontents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.aakira.napier.log
import javazoom.jl.player.FactoryRegistry
import javazoom.jl.player.JavaSoundAudioDeviceFactory
import javazoom.jl.player.jlp

@Composable
actual fun AudioView(obj: RemoteMediaItem, isEmbed: Boolean) {
    val url = obj.url
    log {
        "Audio $url"
    }

    var currentPlaying by remember {
        mutableStateOf(false)
    }

    val player = remember {
        jlp.createInstance(emptyArray()).apply {
            setAudioDevice(FactoryRegistry.systemRegistry().createAudioDevice(JavaSoundAudioDeviceFactory::class.java))
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.stop()
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
            if (currentPlaying) {
                player.stop()
                currentPlaying = false
            } else {
                player.play()
                currentPlaying = true
            }
        }) {
            when {
                currentPlaying -> Icon(Icons.Default.PauseCircle, "pause", modifier = Modifier.size(40.dp))
                else -> Icon(Icons.Default.PlayCircle, "play", modifier = Modifier.size(40.dp))
            }
        }
    }
}
