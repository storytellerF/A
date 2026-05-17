package com.storyteller_f.a.app.core.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.storyteller_f.a.app.core.utils.parseM3UPlayList
import com.storyteller_f.a.client.core.LoadingHandler
import com.storyteller_f.a.client.core.SimpleLoadingHandler
import com.storyteller_f.shared.model.FileInfo
import io.ktor.client.HttpClient

object DefaultMediaPlayListHandlerProvider : MediaPlayListHandlerProvider {
    @Composable
    override fun playListHandler(remoteMediaItem: RemoteMediaItem): LoadingHandler<List<ConstPlayItem>> {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        return remember(remoteMediaItem, context) {
            SimpleLoadingHandler(scope) {
                runCatching {
                    loadMediaPlayList(remoteMediaItem, context)
                }
            }
        }
    }
}

private suspend fun loadMediaPlayList(
    remoteMediaItem: RemoteMediaItem,
    context: Context
): List<ConstPlayItem> = when (remoteMediaItem.contentType) {
    FileInfo.M3U8_MIMETYPE -> parseM3UPlayList(remoteMediaItem, HttpClient { })
    FileInfo.YOUTUBE_MIMETYPE, FileInfo.SOUND_CLOUD_MIME_TYPE -> getPlaylistFromNewPipe(remoteMediaItem, context)
    else -> listOf(ConstPlayItem(remoteMediaItem.url, title = remoteMediaItem.url))
}
