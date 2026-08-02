package com.storyteller_f.a.client.compose_core.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement

@Composable
actual fun VideoViewEmbed(remoteMediaItem: RemoteMediaItem) {
    VideoPlayer(
        remoteMediaItem = remoteMediaItem,
        modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO),
    )
}

@Composable
actual fun VideoViewFilled(remoteMediaItem: RemoteMediaItem) {
    VideoPlayer(remoteMediaItem, Modifier.fillMaxSize())
}

@Composable
actual fun VideoViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    VideoPlayer(remoteMediaItem, Modifier.fillMaxSize())
}

@Composable
actual fun rememberIsInPipMode(): Boolean = false

@OptIn(ExperimentalComposeUiApi::class)
private object VideoPlayer {
    @Composable
    operator fun invoke(remoteMediaItem: RemoteMediaItem, modifier: Modifier) {
        HtmlElementView(
            factory = {
                (document.createElement("video") as HTMLVideoElement).apply {
                    controls = true
                    preload = "metadata"
                    setAttribute("playsinline", "")
                    style.width = "100%"
                    style.height = "100%"
                    style.objectFit = "contain"
                }
            },
            modifier = modifier,
            update = { video ->
                val coverUrl = remoteMediaItem.cover?.url
                if (video.poster != coverUrl.orEmpty()) {
                    video.poster = coverUrl.orEmpty()
                }
                if (video.getAttribute("src") != remoteMediaItem.url) {
                    video.src = remoteMediaItem.url
                    video.load()
                }
            },
            onRelease = { video ->
                video.pause()
                video.removeAttribute("src")
                video.load()
            },
        )
    }
}

private const val VIDEO_ASPECT_RATIO = 16f / 9f
