package com.storyteller_f.a.app.core.components

import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class RemoteMediaItem(
    val id: String,
    val url: String,
    val contentType: String,
    val isM3U8PlayList: Boolean,
    val name: String,
    val cover: FileInfo? = null,
    val title: String? = null
)

sealed interface FileViewData {
    data class Player(val remoteMediaItem: RemoteMediaItem) : FileViewData

    data class Regular(val fileId: PrimaryKey) : FileViewData

    data class LocalImage(val url: String) : FileViewData
}
