package com.storyteller_f.a.app.core.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun AudioViewEmbed(remoteMediaItem: RemoteMediaItem) {
    Text(remoteMediaItem.url)
}

@Composable
actual fun AudioViewFilled(remoteMediaItem: RemoteMediaItem) {
    Text(remoteMediaItem.url)
}

@Composable
actual fun AudioViewFullScreen(remoteMediaItem: RemoteMediaItem) {
    Text(remoteMediaItem.url)
}
