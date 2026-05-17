package com.storyteller_f.a.app.core.components

import kotlinx.coroutines.flow.MutableStateFlow

actual abstract class MediaPlayerService {
    actual val state: MutableStateFlow<MediaPlaySession?> = MutableStateFlow(null)
    actual abstract fun fullscreen(remoteMediaItem: RemoteMediaItem)
    actual abstract suspend fun start(
        remoteMediaItem: RemoteMediaItem,
        localMediaPlaySession: LocalMediaPlaySession,
        playList: List<ConstPlayItem>
    )

    actual abstract val enablePip: Boolean
}
