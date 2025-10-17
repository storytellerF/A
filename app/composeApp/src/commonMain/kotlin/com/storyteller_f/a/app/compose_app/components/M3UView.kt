package com.storyteller_f.a.app.compose_app.components

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bjoernpetersen.m3u.M3uParser

suspend fun parseM3UPlayList(
    obj: RemoteMediaItem,
    client: HttpClient
): List<ConstPlayItem> =
    if ((obj.url.startsWith("http://") || obj.url.startsWith("https://")) && obj.isM3U8PlayList) {
        val entries = withContext(Dispatchers.IO) {
            val content = client.get(obj.url).bodyAsText()
            M3uParser.parse(content)
        }
        entries.map {
            ConstPlayItem(it.location.url.toString(), it.metadata["tvg-logo"], it.title)
        }.distinctBy {
            it.id
        }
    } else {
        listOf(ConstPlayItem(obj.url, "", obj.url))
    }
