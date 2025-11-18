package com.storyteller_f.a.app.core.components

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface PlayItem {
    val id: String
    val icon: String?
    val title: String?
}

@Serializable
data class ConstPlayItem(
    val url: String,
    override val icon: String? = null,
    override val title: String? = null
) : PlayItem {
    override val id: String
        get() = url
}

@OptIn(ExperimentalUuidApi::class)
data class LocalMediaPlaySession(val id: String, val uuid: Uuid)

@Serializable
data class CustomVideoSize(val width: Int, val height: Int)

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class MediaPlaySession(
    val remoteMediaItem: RemoteMediaItem,
    val playList: List<ConstPlayItem>,
    val uuids: List<Uuid>,
    val videoSize: CustomVideoSize?,
) {
    val id = remoteMediaItem.url

    val lastUuid get() = uuids.lastOrNull()

    @OptIn(ExperimentalUuidApi::class)
    fun appendUuid(
        uuid: Uuid
    ) = copy(uuids = uuids + uuid)

    val uuidCount get() = uuids.size
}

val LocalMediaPlayerService = compositionLocalOf<MediaPlayerService> {
    error("LocalMediaPlayerComponent not provided")
}

expect abstract class MediaPlayerService {
    val state: MutableStateFlow<MediaPlaySession?>

    abstract fun fullscreen(remoteMediaItem: RemoteMediaItem)
}
