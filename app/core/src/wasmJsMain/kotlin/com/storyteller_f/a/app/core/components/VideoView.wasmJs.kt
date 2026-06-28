package com.storyteller_f.a.app.core.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun VideoViewEmbed(remoteMediaItem: RemoteMediaItem) {
    Text(remoteMediaItem.url)
}

@Composable
actual fun VideoViewFilled(remoteMediaItem: RemoteMediaItem) {
    Text(remoteMediaItem.url)
}

@Composable
actual fun VideoViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    Text(remoteMediaItem.url)
}

@Composable
actual fun rememberIsInPipMode(): Boolean = false
