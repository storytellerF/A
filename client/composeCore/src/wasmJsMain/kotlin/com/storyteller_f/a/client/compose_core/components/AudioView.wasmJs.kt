package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

@Composable
actual fun AudioViewEmbed(remoteMediaItem: RemoteMediaItem) {
    AudioPlayer(remoteMediaItem)
}

@Composable
actual fun AudioViewFilled(remoteMediaItem: RemoteMediaItem) {
    AudioPlayer(remoteMediaItem)
}

@Composable
actual fun AudioViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    AudioPlayer(remoteMediaItem)
}

@OptIn(ExperimentalComposeUiApi::class)
private object AudioPlayer {
    @Composable
    operator fun invoke(remoteMediaItem: RemoteMediaItem) {
        HtmlElementView(
            factory = {
                (document.createElement("audio") as HTMLAudioElement).apply {
                    controls = true
                    preload = "metadata"
                    style.width = "100%"
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            update = { audio ->
                if (audio.getAttribute("src") != remoteMediaItem.url) {
                    audio.src = remoteMediaItem.url
                    audio.load()
                }
            },
            onRelease = { audio ->
                audio.pause()
                audio.removeAttribute("src")
                audio.load()
            },
        )
    }
}
