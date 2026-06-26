package com.storyteller_f.a.app.core.utils

import com.storyteller_f.a.app.core.components.ConstPlayItem
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bjoernpetersen.m3u.M3uParser

actual suspend fun parseM3UPlayList(
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
