package com.storyteller_f.a.app.core.utils

import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.client.core.UploadData
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import net.bjoernpetersen.m3u.M3uParser

suspend fun parseM3UPlayList(
    remoteMediaItem: RemoteMediaItem,
    client: HttpClient
): List<ConstPlayItem> =
    if ((remoteMediaItem.url.startsWith("http://") ||
            remoteMediaItem.url.startsWith("https://")) && remoteMediaItem.isM3U8PlayList
    ) {
        val entries = withContext(Dispatchers.IO) {
            val content = client.get(remoteMediaItem.url).bodyAsText()
            M3uParser.parse(content)
        }
        entries.map {
            ConstPlayItem(it.location.url.toString(), it.metadata["tvg-logo"], it.title)
        }.distinctBy {
            it.id
        }
    } else {
        listOf(ConstPlayItem(remoteMediaItem.url, "", remoteMediaItem.url))
    }

fun getUploadDataFromPath(
    meta: FileMetadata,
    path: Path
) = UploadData(meta.size, path.name, ContentType.defaultForFileExtension(path.toString())) {
    SystemFileSystem.source(path).buffered()
}
