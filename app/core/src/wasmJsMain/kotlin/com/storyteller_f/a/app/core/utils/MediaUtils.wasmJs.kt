package com.storyteller_f.a.app.core.utils

import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import io.ktor.client.HttpClient

// wasm 上没有 m3u-parser；回退为不展开播放列表，直接以原始 url 作为单条目。
@Suppress("UNUSED_PARAMETER")
actual suspend fun parseM3UPlayList(
    remoteMediaItem: RemoteMediaItem,
    client: HttpClient
): List<ConstPlayItem> = listOf(ConstPlayItem(remoteMediaItem.url, "", remoteMediaItem.url))
