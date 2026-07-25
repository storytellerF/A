package com.storyteller_f.a.client.compose_core.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.storyteller_f.a.client.compose_core.utils.parseM3UPlayList
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.SimpleLoadingHandler
import com.storyteller_f.shared.model.FileInfo
import io.ktor.client.HttpClient

object DefaultMediaPlayListHandlerProvider : MediaPlayListHandlerProvider {
    @Composable
    override fun playListHandler(remoteMediaItem: RemoteMediaItem): LoadingHandler<List<ConstPlayItem>> {
        val scope = rememberCoroutineScope()
        return remember(remoteMediaItem) {
            SimpleLoadingHandler(scope) {
                runCatching {
                    loadMediaPlayList(remoteMediaItem)
                }
            }
        }
    }
}

private suspend fun loadMediaPlayList(
    remoteMediaItem: RemoteMediaItem
): List<ConstPlayItem> = when (remoteMediaItem.contentType) {
    FileInfo.M3U8_MIMETYPE -> parseM3UPlayList(remoteMediaItem, HttpClient { })
    else -> listOf(ConstPlayItem(remoteMediaItem.url, title = remoteMediaItem.url))
}
