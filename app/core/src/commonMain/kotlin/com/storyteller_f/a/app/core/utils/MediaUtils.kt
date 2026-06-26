package com.storyteller_f.a.app.core.utils

import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.client.core.UploadData
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

// 解析 m3u 播放列表。jvm/android 用 m3u-parser；wasm 上无该库，回退为单条目（见各平台 actual）。
expect suspend fun parseM3UPlayList(
    remoteMediaItem: RemoteMediaItem,
    client: HttpClient
): List<ConstPlayItem>

fun getUploadDataFromPath(
    meta: FileMetadata,
    path: Path,
    sha256: String,
) = UploadData(meta.size, path.name, ContentType.defaultForFileExtension(path.toString()), sha256) {
    SystemFileSystem.source(path).buffered()
}
