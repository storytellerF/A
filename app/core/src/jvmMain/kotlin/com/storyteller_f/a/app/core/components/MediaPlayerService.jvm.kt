package com.storyteller_f.a.app.core.components

import kotlinx.coroutines.flow.MutableStateFlow

actual abstract class MediaPlayerService {
    actual val state: MutableStateFlow<MediaPlaySession?> = MutableStateFlow(null)
    actual abstract fun fullscreen(remoteMediaItem: RemoteMediaItem)
}
